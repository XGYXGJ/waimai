"""AI 模型池注册表。

配置来源优先级：MySQL `ai_model` 表 → 本地 data/ai_models.json → 内置默认。
MySQL 查询成功后会把结果写入 JSON 作为兜底快照，因此后端短暂不可用时
AI 服务仍能按上一次的配置继续工作。

失败冷却（重要）：某个模型刚失败过，就在一段时间内不再排在前面。
没有这层保护时，每次请求都要先等一个已经挂掉的高优先级模型把 timeout 耗完
（zen 默认 60s），用户侧感知就是「客服半天不回」。
"""
import json
import threading
import time
import uuid
from pathlib import Path
from typing import Any

from ..config import settings

# 首次失败的冷却时长；连续失败按倍数递增，上限 MAX_COOLDOWN_SEC
FAIL_COOLDOWN_SEC = 60
MAX_COOLDOWN_SEC = 600

# MySQL 完全不可用时的内置兜底（zen 免费模型 + 本地 Ollama）
DEFAULT_MODELS: list[dict[str, Any]] = [
    {"id": 0, "name": "OpenCode Zen 免费模型", "provider": "zen", "base_url": None,
     "api_key": None, "model_id": "mimo-v2.6-flash-free", "enabled": 1, "priority": 10,
     "timeout": 60, "remark": "内置默认（免密钥）"},
    {"id": 0, "name": "本地 Ollama", "provider": "ollama",
     "base_url": "http://host.docker.internal:11434", "api_key": None,
     "model_id": "qwen2.5:7b-instruct", "enabled": 1, "priority": 40,
     "timeout": 60, "remark": "内置默认（本地兜底）"},
]

_JSON_PATH = Path(__file__).resolve().parents[2] / "data" / "ai_models.json"

_SELECT_SQL = ("SELECT id, name, provider, base_url, api_key, model_id, "
               "enabled, priority, timeout, remark FROM ai_model "
               "ORDER BY priority ASC, id ASC")


def _from_mysql() -> tuple[list[dict[str, Any]], str]:
    """返回 (模型列表, 错误描述)。第二项用于解释「为什么回退到了默认配置」。"""
    try:
        import pymysql  # 可选依赖，未安装时自动降级
    except ImportError:
        return [], "未安装 pymysql（pip install pymysql）"
    conn = None
    try:
        conn = pymysql.connect(
            host=settings.mysql_host, port=settings.mysql_port,
            user=settings.mysql_user, password=settings.mysql_password,
            database=settings.mysql_db, charset="utf8mb4",
            connect_timeout=3, read_timeout=5,
        )
        with conn.cursor(pymysql.cursors.DictCursor) as cur:
            cur.execute(_SELECT_SQL)
            rows = cur.fetchall()
        return [_norm(r) for r in rows], ""
    except Exception as e:  # noqa: BLE001
        return [], f"{type(e).__name__}: {e}"[:200]
    finally:
        if conn is not None:
            try:
                conn.close()
            except Exception:
                pass


def _norm(r: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": r.get("id") or 0,
        "name": r.get("name") or "",
        "provider": (r.get("provider") or "openai").lower(),
        "base_url": r.get("base_url") or None,
        "api_key": r.get("api_key") or None,
        "model_id": r.get("model_id") or "",
        "enabled": int(r.get("enabled") if r.get("enabled") is not None else 1),
        "priority": int(r.get("priority") or 100),
        "timeout": int(r.get("timeout") or 30),
        "remark": r.get("remark") or "",
    }


def model_key(m: dict[str, Any]) -> str:
    return f"{m.get('provider')}:{m.get('model_id')}"


class ModelRegistry:
    """模型列表缓存 + 失败冷却 + 调用结果记录（供管理端查看当前生效模型）。"""

    def __init__(self) -> None:
        self._cache: list[dict[str, Any]] = []
        self._ts = 0.0
        self._source = "init"
        self._mysql_error = ""
        self._status: dict[str, dict[str, Any]] = {}
        self._cooldown: dict[str, float] = {}   # key -> 冷却截止时间戳
        self._fails: dict[str, int] = {}        # key -> 连续失败次数
        # 端点声明成同步 def，FastAPI 会用线程池并发执行，所以要加锁
        self._lock = threading.Lock()

    # ---------- 加载 ----------

    def all(self) -> list[dict[str, Any]]:
        with self._lock:
            if self._cache and time.time() - self._ts < settings.ai_model_cache_ttl:
                return self._cache
            rows, err = _from_mysql()
            self._mysql_error = err
            if rows:
                self._source = "mysql"
                self._save_snapshot(rows)
            else:
                rows = self._load_snapshot()
                self._source = "json" if rows else "default"
                if not rows:
                    rows = [dict(m) for m in DEFAULT_MODELS]
            self._cache = rows
            self._ts = time.time()
            return rows

    def enabled(self) -> list[dict[str, Any]]:
        """按优先级升序给出候选（即降级顺序）。

        处于冷却期的模型**排到末尾而不是丢掉**：正常情况下它不会挡在健康模型
        前面白白浪费一次超时；但若其它模型全挂了，它仍会被最后一试。
        """
        ready = [m for m in self.all() if self.usable(m)]
        now = time.time()
        healthy, cooling = [], []
        for m in ready:
            (cooling if self._cooldown.get(model_key(m), 0) > now else healthy).append(m)
        return healthy + cooling

    def usable(self, m: dict[str, Any]) -> bool:
        """未启用、或缺 API Key 的模型直接跳过，避免无谓等待。"""
        if int(m.get("enabled", 0)) != 1:
            return False
        if m.get("provider") == "openai" and not (m.get("api_key") or "").strip():
            return False
        return bool(m.get("model_id"))

    def invalidate(self) -> None:
        """管理端改过配置后调用：清缓存 + 清冷却，让改动立即生效。"""
        with self._lock:
            self._ts = 0.0
            self._cooldown.clear()
            self._fails.clear()
            self._status.clear()

    def source(self) -> str:
        return self._source

    # ---------- 快照 ----------

    def _save_snapshot(self, rows: list[dict[str, Any]]) -> None:
        try:
            _JSON_PATH.parent.mkdir(parents=True, exist_ok=True)
            _JSON_PATH.write_text(
                json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
        except Exception:
            pass

    def _load_snapshot(self) -> list[dict[str, Any]]:
        try:
            if not _JSON_PATH.exists():
                return []
            data = json.loads(_JSON_PATH.read_text(encoding="utf-8"))
            return [_norm(r) for r in data]
        except Exception:
            return []

    # ---------- 调用状态 ----------

    def record(self, m: dict[str, Any], ok: bool, error: str = "") -> None:
        key = model_key(m)
        now = time.time()
        with self._lock:
            self._status[key] = {
                "name": m.get("name"),
                "provider": m.get("provider"),
                "modelId": m.get("model_id"),
                "ok": ok,
                "error": error[:200],
                "ts": now,
            }
            if ok:
                self._fails.pop(key, None)
                self._cooldown.pop(key, None)
                self._status["__current__"] = {
                    "name": m.get("name"),
                    "provider": m.get("provider"),
                    "modelId": m.get("model_id"),
                    "ts": now,
                }
            else:
                fails = self._fails.get(key, 0) + 1
                self._fails[key] = fails
                penalty = min(FAIL_COOLDOWN_SEC * fails, MAX_COOLDOWN_SEC)
                self._cooldown[key] = now + penalty

    def status(self) -> dict[str, Any]:
        # 先触发一次加载：否则进程刚起来、还没人调用过模型时，source 一直是
        # "init"，管理端就只能看到一个没有意义的「未知」来源。
        # 必须在取锁之前调用（all() 内部也要抢同一把锁，会死锁）。
        self.all()
        now = time.time()
        with self._lock:
            models = {}
            for k, v in self._status.items():
                if k == "__current__":
                    continue
                left = int(max(0, self._cooldown.get(k, 0) - now))
                models[k] = {**v, "cooldownSec": left, "fails": self._fails.get(k, 0)}
            return {
                "source": self._source,
                "mysqlError": self._mysql_error or None,
                "current": self._status.get("__current__"),
                "models": models,
            }


def new_session() -> str:
    """OpenCode Zen 门禁要求的 session 格式：ses_ + 26 位十六进制。"""
    return "ses_" + uuid.uuid4().hex[:26]


_registry = ModelRegistry()


def get_registry() -> ModelRegistry:
    return _registry
