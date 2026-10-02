"""POST /ai/sentiment 评论情感分析。"""
import json

from fastapi import APIRouter, HTTPException, Header, Depends

from ..config import settings
from ..llm.client import get_llm, LlmError
from ..llm.prompts import SENTIMENT_SYSTEM

router = APIRouter()


def verify_token(x_internal_token: str = Header(default="")):
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")


@router.post("/sentiment")
async def sentiment(payload: dict, _: str = Depends(verify_token)):
    content = payload.get("content", "")
    messages = [
        {"role": "system", "content": SENTIMENT_SYSTEM.format(content=content)},
        {"role": "user", "content": content},
    ]
    try:
        raw = get_llm().chat(messages, json_mode=True, max_tokens=100)
        result = json.loads(raw)
        return {"sentiment": result.get("sentiment", "NEU"),
                "score": result.get("score", 0.5), "degraded": False}
    except (LlmError, json.JSONDecodeError, Exception):
        return {"sentiment": "NEU", "score": 0.5, "degraded": True}
