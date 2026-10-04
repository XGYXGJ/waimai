"""管理端辅助接口：模型连通性测试、模型池运行状态。

由后端（Spring Boot）以内部 token 调用，不对用户端暴露。
"""
from fastapi import APIRouter, Depends, Header, HTTPException

import httpx

from ..config import settings
from ..llm.client import LlmClient, ZEN_UA
from ..llm.registry import get_registry, new_session

router = APIRouter()

# 免密钥的 zen 模型：带 -free 后缀，或官方点名的白名单模型
ZEN_MODELS_URL = "https://opencode.ai/inference/v1/models"
ZEN_FREE_WHITELIST = {"big-pickle"}


def verify_token(x_internal_token: str = Header(default="")):
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")


def _to_model(payload: dict) -> dict:
    """后端传的是驼峰字段，转成内部使用的下划线结构。"""
    return {
        "id": payload.get("id") or 0,
        "name": payload.get("name") or "测试模型",
        "provider": (payload.get("provider") or "openai").lower(),
        "base_url": payload.get("baseUrl") or payload.get("base_url"),
        "api_key": payload.get("apiKey") or payload.get("api_key") or "",
        "model_id": payload.get("modelId") or payload.get("model_id") or "",
        "enabled": 1,
        "priority": 0,
        "timeout": int(payload.get("timeout") or 30),
    }


@router.post("/test-model")
def test_model(payload: dict, _: str = Depends(verify_token)):
    """用表单里的配置实跑一次，不落库。同步 def：内部是阻塞 HTTP 调用。"""
    model = _to_model(payload)
    if not model["model_id"]:
        return {"ok": False, "message": "模型标识为空"}
    if model["provider"] == "openai" and not (model["api_key"] or "").strip():
        return {"ok": False, "message": "未填写 API Key"}
    try:
        text = LlmClient()._dispatch(
            model,
            [{"role": "user", "content": "只回复两个字：正常"}],
            False, 0.1, 60,
        )
        text = (text or "").strip()
        return {"ok": True, "message": f"连通正常，模型返回：{text[:60] or '(空)'}"}
    except Exception as e:
        # 带上目标端点，避免「模型名填错位置」时看不出请求发到了哪里
        target = model["base_url"] or (
            "opencode.ai/zen" if model["provider"] == "zen" else "(未填地址)")
        return {"ok": False,
                "message": f"[{model['provider']}] -> {target} : {type(e).__name__}: {e}"[:300]}


@router.get("/models/status")
def models_status(_: str = Depends(verify_token)):
    """模型池来源、当前生效模型、各模型最近一次调用结果与冷却剩余时间。"""
    return get_registry().status()


@router.post("/models/reload")
def models_reload(_: str = Depends(verify_token)):
    """管理端改过模型配置后调用：清缓存与失败冷却，让改动立即生效。

    否则改动最多要等 ai_model_cache_ttl 秒才生效，而且刚修好密钥的模型
    可能还在冷却期里继续被排到最后。
    """
    get_registry().invalidate()
    return {"ok": True}


@router.post("/models/catalog")
def models_catalog(payload: dict, _: str = Depends(verify_token)):
    """列出该服务商「当前网络环境下」真实可用的模型名，供管理端下拉选择。

    手填模型名极易出错（例如把 zen 的模型名填到 DeepSeek 端点上，
    DeepSeek 会回复「supported API model names are deepseek-flash / deepseek-v4-pro」）。
    """
    provider = (payload.get("provider") or "zen").lower()
    base_url = (payload.get("baseUrl") or "").rstrip("/")
    api_key = payload.get("apiKey") or ""
    try:
        if provider == "zen":
            models = _zen_models()
        elif provider == "ollama":
            models = _ollama_models(base_url or settings.ollama_base_url)
        else:
            models = _openai_models(base_url, api_key)
        return {"ok": True, "provider": provider, "models": models}
    except Exception as e:
        return {"ok": False, "provider": provider, "models": [],
                "message": f"{type(e).__name__}: {e}"[:300]}


def _zen_models():
    """zen 免费模型池会按地区轮换，必须实时拉取，不能写死。"""
    headers = {"User-Agent": ZEN_UA, "x-opencode-session": new_session()}
    r = httpx.get(ZEN_MODELS_URL, headers=headers, timeout=30)
    r.raise_for_status()
    items = []
    for m in (r.json().get("data") or []):
        mid = m.get("id")
        if not mid:
            continue
        items.append({"id": mid,
                      "free": mid.endswith("-free") or mid in ZEN_FREE_WHITELIST})
    # 免费模型排前面，方便直接选
    items.sort(key=lambda x: (not x["free"], x["id"]))
    return items


def _openai_models(base_url: str, api_key: str):
    if not base_url:
        raise ValueError("请先填写 API 地址")
    # Key 为空时不能发 "Bearer "（httpx 会直接抛非法 header）
    headers = {"Authorization": f"Bearer {api_key}"} if api_key.strip() else {}
    last = None
    for url in (base_url + "/models", base_url + "/v1/models"):
        try:
            r = httpx.get(url, headers=headers, timeout=20)
            r.raise_for_status()
            data = r.json().get("data") or []
            return [{"id": m.get("id"), "free": False} for m in data if m.get("id")]
        except Exception as e:      # noqa: BLE001
            last = e
            continue
    raise last if last else ValueError("未能获取模型列表")


def _ollama_models(base_url: str):
    r = httpx.get(base_url.rstrip("/") + "/api/tags", timeout=20)
    r.raise_for_status()
    data = r.json().get("models") or []
    return [{"id": m.get("name"), "free": True} for m in data if m.get("name")]
