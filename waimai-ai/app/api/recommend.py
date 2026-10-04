"""POST /ai/recommend 每日推荐理由。"""
import json

from fastapi import APIRouter, HTTPException, Header, Depends

from ..config import settings
from ..llm.client import get_llm, parse_json, LlmError
from ..llm.prompts import RECOMMEND_SYSTEM

router = APIRouter()


def verify_token(x_internal_token: str = Header(default="")):
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")


@router.post("/recommend")
def recommend(payload: dict, _: str = Depends(verify_token)):
    # 同步 def（不要 async）：LLM 调用是阻塞的，交给 FastAPI 线程池执行
    candidates = payload.get("candidates") or []
    context = payload.get("context") or {}
    profile = payload.get("userProfile") or {}

    user_msg = {
        "candidates": candidates,
        "userProfile": profile,
        "context": context,
    }
    messages = [
        {"role": "system", "content": RECOMMEND_SYSTEM},
        {"role": "user", "content": json.dumps(user_msg, ensure_ascii=False)},
    ]
    try:
        raw = get_llm().chat(messages, json_mode=True, max_tokens=500)
        items = parse_json(raw)
        if isinstance(items, dict) and "items" in items:
            items = items["items"]
        return {"items": items, "degraded": False}
    except Exception:      # noqa: BLE001  解析失败/模型全挂 → 交给后端走非 AI 排序
        return {"items": [], "degraded": True}
