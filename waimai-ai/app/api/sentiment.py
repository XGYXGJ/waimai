"""POST /ai/sentiment 评论情感分析。"""
import json

from fastapi import APIRouter, HTTPException, Header, Depends

from ..config import settings
from ..llm.client import get_llm, parse_json, LlmError
from ..llm.prompts import SENTIMENT_SYSTEM

router = APIRouter()


def verify_token(x_internal_token: str = Header(default="")):
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")


@router.post("/sentiment")
def sentiment(payload: dict, _: str = Depends(verify_token)):
    # 同步 def（不要 async）：LLM 调用是阻塞的，交给 FastAPI 线程池执行
    content = payload.get("content", "")
    messages = [
        # 注意：这里不能用 .format()。SENTIMENT_SYSTEM 里自带的 JSON 示例
        # {"sentiment":"POS|NEU|NEG",...} 会被 str.format 当成占位符，直接抛
        # KeyError: '"sentiment"'（该接口此前一直 500）。用 replace 保持模板原文不变。
        {"role": "system", "content": SENTIMENT_SYSTEM.replace("{content}", content)},
        {"role": "user", "content": content},
    ]
    try:
        raw = get_llm().chat(messages, json_mode=True, max_tokens=100)
        result = parse_json(raw)
        return {"sentiment": result.get("sentiment", "NEU"),
                "score": result.get("score", 0.5), "degraded": False}
    except Exception:      # noqa: BLE001  解析失败/模型全挂 → 中性
        return {"sentiment": "NEU", "score": 0.5, "degraded": True}
