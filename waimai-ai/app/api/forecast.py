"""POST /ai/forecast 销量预测（纯算法，无 LLM）。"""
from fastapi import APIRouter, HTTPException, Header, Depends

from ..config import settings
from ..svc.forecast_core import forecast_dish

router = APIRouter()


def verify_token(x_internal_token: str = Header(default="")):
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")


@router.post("/forecast")
def forecast(payload: dict, _: str = Depends(verify_token)):
    """纯算法，无 LLM 调用，所以不需要降级链；但要防止脏数据把接口打崩。"""
    dishes = payload.get("dishes")
    if not isinstance(dishes, list):
        dishes = []
    items, degraded = [], False
    for d in dishes:
        if not isinstance(d, dict):
            continue
        try:
            f = forecast_dish(d.get("sales") or [])
        except Exception:      # noqa: BLE001  单条菜品数据异常不该拖垮整张预测表
            f, degraded = 0, True
        items.append({
            "dishId": d.get("dishId"),
            "forecast": f,
            "suggestion": int(f * 1.1),
        })
    return {"items": items, "degraded": degraded}
