"""FastAPI 入口。"""
from fastapi import FastAPI

from .api import chat, recommend, sentiment, forecast

app = FastAPI(title="Waimai AI Service")

app.include_router(chat.router, prefix="/ai", tags=["chat"])
app.include_router(recommend.router, prefix="/ai", tags=["recommend"])
app.include_router(sentiment.router, prefix="/ai", tags=["sentiment"])
app.include_router(forecast.router, prefix="/ai", tags=["forecast"])


@app.get("/health")
async def health():
    return {"status": "ok"}
