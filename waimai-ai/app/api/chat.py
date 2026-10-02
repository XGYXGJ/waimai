"""POST /ai/chat 智能客服。"""
import json

from fastapi import APIRouter, HTTPException, Header, Depends

from ..config import settings
from ..llm.client import get_llm, LlmError
from ..llm.prompts import CUSTOMER_SERVICE_SYSTEM
from ..svc.intent import route_intent

router = APIRouter()


def verify_token(x_internal_token: str = Header(default="")):
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")


@router.post("/chat")
async def chat(payload: dict, _: str = Depends(verify_token)):
    message = payload.get("message", "")
    intent = route_intent(message)
    order_ctx = payload.get("orderContext") or {}
    order_text = json.dumps(order_ctx, ensure_ascii=False) if order_ctx else "用户当前无进行中的订单"

    history = payload.get("history") or []
    messages = [{"role": "system", "content": CUSTOMER_SERVICE_SYSTEM.format(order_context=order_text)}]
    messages.extend(history)
    messages.append({"role": "user", "content": message})

    try:
        reply = get_llm().chat(messages, json_mode=False, max_tokens=200)
        return {"reply": reply, "intent": intent, "degraded": False}
    except LlmError:
        # 降级：由后端根据 intent 走固定话术
        return {"reply": "", "intent": intent, "degraded": True}
