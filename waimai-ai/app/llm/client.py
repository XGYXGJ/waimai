"""统一 LLM 客户端：按 ai_model 优先级依次尝试，失败自动降级到下一个。

provider 支持：
  openai —— OpenAI 兼容接口（DeepSeek / 通义 / 自建网关等），需 API Key
  zen    —— OpenCode 免费模型，免密钥，内部补齐门禁（UA / session / 工具声明 / 流式）
  ollama —— 本地 Ollama，免密钥兜底
"""
import json

import httpx

from ..config import settings
from .registry import get_registry, new_session

# ---- OpenCode Zen 免费层门禁（缺任一条件即 403 FreeTierError）----
ZEN_UPSTREAM = "https://opencode.ai/zen/v1/chat/completions"
ZEN_UA = "opencode/1.18.13"  # 必须 >= 1.18
ZEN_GATE_TOOLS = [
    {"type": "function", "function": {"name": n, "description": n,
                                      "parameters": {"type": "object",
                                                     "properties": {"p": {"type": "string"}},
                                                     "required": ["p"]}}}
    for n in ("bash", "glob", "grep", "read")
]

JSON_HINT = "请严格输出合法 JSON，不要包含任何解释性文字或代码块标记。"


class LlmClient:
    """统一调用入口。模型池全部失败时抛 LlmError，由上层返回 degraded 标记。"""

    def __init__(self):
        self._registry = get_registry()

    def chat(self, messages, json_mode=False, temperature=0.7, max_tokens=500):
        """messages: [{"role": "system"|"user"|"assistant", "content": "..."}]"""
        models = self._registry.enabled()
        if not models:
            raise LlmError("无可用模型：模型池为空，或全部未启用 / 未填写 API Key")

        errors = []
        for m in models:
            try:
                text = self._dispatch(m, messages, json_mode, temperature, max_tokens)
                self._registry.record(m, True)
                return text
            except Exception as e:
                msg = f"{type(e).__name__}: {e}"
                errors.append(f"{m.get('name')}({m.get('model_id')}) {msg}")
                self._registry.record(m, False, msg)
                continue
        raise LlmError("降级链全部失败 -> " + " | ".join(errors))

    def _dispatch(self, m, messages, json_mode, temperature, max_tokens):
        provider = m.get("provider")
        if provider == "zen":
            return self._zen_chat(m, messages, json_mode, temperature, max_tokens)
        if provider == "ollama":
            return self._ollama_chat(m, messages, json_mode, temperature, max_tokens)
        return self._openai_chat(m, messages, json_mode, temperature, max_tokens)

    # ---------- OpenAI 兼容 ----------

    def _openai_chat(self, m, messages, json_mode, temperature, max_tokens):
        base = (m.get("base_url") or "").rstrip("/")
        if not base:
            raise RuntimeError("未配置 API 地址")
        payload = {
            "model": m["model_id"],
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream": False,
        }
        if json_mode:
            payload["response_format"] = {"type": "json_object"}
        headers = {
            "Authorization": f"Bearer {m.get('api_key') or ''}",
            "Content-Type": "application/json",
        }
        timeout = m.get("timeout") or settings.cloud_timeout
        # 兼容两种 base_url 写法：.../v1 与直接域名。
        # 只在 404（路径写错）时才换另一种写法；401/429/超时/连不上时换路径结果一样，
        # 直接抛出交给降级链，避免失败延迟翻倍。
        last_status = None
        for path in ("/chat/completions", "/v1/chat/completions"):
            r = httpx.post(base + path, json=payload, headers=headers, timeout=timeout)
            last_status = r.status_code
            if r.status_code == 404:
                continue
            r.raise_for_status()
            return r.json()["choices"][0]["message"]["content"]
        raise RuntimeError(
            f"两个路径都返回 404，请检查 API 地址：{base}/chat/completions"
            f"、{base}/v1/chat/completions（末次 {last_status}）")

    # ---------- OpenCode Zen 免费模型 ----------

    def _zen_chat(self, m, messages, json_mode, temperature, max_tokens):
        body = {
            "model": m["model_id"],
            "messages": _with_json_hint(messages, json_mode),
            "temperature": temperature,
            # 免费层多为推理模型，thinking 会占用预算，过小会导致正文为空
            "max_tokens": max(int(max_tokens or 0) * 6, 800),
            "stream": True,          # 上行强制流式，false 会 403
            "tools": ZEN_GATE_TOOLS,  # 上行必须携带门禁工具
        }
        headers = {
            "Content-Type": "application/json",
            "User-Agent": ZEN_UA,
            "x-opencode-session": new_session(),
            "Authorization": "Bearer public",
        }
        timeout = m.get("timeout") or 60
        with httpx.stream("POST", ZEN_UPSTREAM, json=body, headers=headers,
                          timeout=timeout) as r:
            r.raise_for_status()
            text = _aggregate_sse(r.iter_lines()).strip()
            if not text:
                # 空回复比报错更糟：抛错让降级链尝试下一个模型
                raise RuntimeError("模型返回空内容（可能被思考过程耗尽 token 预算）")
            return text

    # ---------- 本地 Ollama ----------

    def _ollama_chat(self, m, messages, json_mode, temperature, max_tokens):
        base = (m.get("base_url") or settings.ollama_base_url).rstrip("/")
        # 注意：/api/chat 只认 messages。若误传 prompt 字段，Ollama 会返回
        # done_reason=load 的空回复（不报错），看起来像「模型不回复」。
        payload = {
            "model": m["model_id"],
            "messages": _with_json_hint(messages, json_mode),
            "stream": False,
            "options": {
                "temperature": temperature,
                # 本地模型同样多为推理模型，thinking 会吃掉输出预算，留足余量
                "num_predict": max(int(max_tokens or 0) * 6, 1024),
            },
        }
        r = httpx.post(base + "/api/chat", json=payload,
                       timeout=m.get("timeout") or settings.ollama_timeout)
        r.raise_for_status()
        body = r.json()
        if body.get("error"):
            raise RuntimeError(str(body["error"])[:200])
        msg = body.get("message") or {}
        text = (msg.get("content") or "").strip()
        if not text:
            # 空回复比报错更糟：抛错让降级链继续尝试下一个模型
            raise RuntimeError("模型返回空内容"
                               + ("（思考过程占满了输出预算）" if msg.get("thinking") else ""))
        return text


def _with_json_hint(messages, json_mode):
    """zen / ollama 不支持 response_format，改为在 system 提示里约束。"""
    if not json_mode:
        return messages
    out = list(messages)
    for i, msg in enumerate(out):
        if msg.get("role") == "system":
            out[i] = {"role": "system", "content": msg["content"] + "\n" + JSON_HINT}
            return out
    return [{"role": "system", "content": JSON_HINT}] + out


def parse_json(text):
    """容错解析模型返回的 JSON。

    即使提示里要求「只输出 JSON」，模型仍常包上 ```json 代码块或加一句解释，
    直接 json.loads 会失败并让推荐/情感分析静默降级为空结果。
    """
    s = (text or "").strip()
    if s.startswith("```"):
        nl = s.find("\n")
        if nl != -1:
            s = s[nl + 1:]
        s = s.rstrip()
        if s.endswith("```"):
            s = s[:-3].rstrip()
    try:
        return json.loads(s)
    except json.JSONDecodeError:
        pass
    # 退一步：截取第一个 { 或 [ 到最后一个配对符号
    for op, cl in (("{", "}"), ("[", "]")):
        i, j = s.find(op), s.rfind(cl)
        if i != -1 and j > i:
            try:
                return json.loads(s[i:j + 1])
            except json.JSONDecodeError:
                continue
    raise ValueError(f"模型返回的不是合法 JSON：{s[:120]}")


def _aggregate_sse(lines) -> str:
    """把上游 SSE 聚合成纯文本（zen 只接受流式）。"""
    parts = []
    for line in lines:
        if not line:
            continue
        if isinstance(line, bytes):
            line = line.decode("utf-8", "replace")
        line = line.strip()
        if not line.startswith("data:"):
            continue
        data = line[5:].strip()
        if data == "[DONE]":
            break
        try:
            chunk = json.loads(data)
        except json.JSONDecodeError:
            continue
        for ch in chunk.get("choices") or []:
            delta = ch.get("delta") or {}
            if delta.get("content"):
                parts.append(delta["content"])
    return "".join(parts)


class LlmError(Exception):
    pass


_llm = LlmClient()


def get_llm() -> LlmClient:
    return _llm
