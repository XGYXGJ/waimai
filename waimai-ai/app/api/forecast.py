"""POST /ai/forecast 销量预测（纯算法，无 LLM）。"""
from fastapi import APIRouter, HTTPException, Header, Depends

from ..config import settings
from ..svc.forecast_core import forecast_dish

router = APIRouter()


def verify_token(x_internal_token: str = Header(default="")):
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")


@router.post("/forecast")
async def forecast(payload: dict, _: str = Depends(verify_token)):
    dishes = payload.get("dishes") or []
    items = []
    for d in dishes:
        f = forecast_dish(d.get("sales") or [])
        items.append({
            "dishId": d.get("dishId"),
            "forecast": f,
            "suggestion": int(f * 1.1),
        })
    return {"items": items, "degraded": False}
