"""FastAPI 入口。"""
from fastapi import FastAPI

from .api import admin, chat, recommend, sentiment, forecast

app = FastAPI(title="Waimai AI Service")

app.include_router(chat.router, prefix="/ai", tags=["chat"])
app.include_router(recommend.router, prefix="/ai", tags=["recommend"])
app.include_router(sentiment.router, prefix="/ai", tags=["sentiment"])
app.include_router(forecast.router, prefix="/ai", tags=["forecast"])
app.include_router(admin.router, prefix="/ai/admin", tags=["admin"])


@app.get("/health")
async def health():
    # api 版本号：管理端/后端可据此判断实例是否为最新代码（旧镜像缺 /ai/admin/* 会 404）
    return {"status": "ok", "service": "waimai-ai", "api": 2}
