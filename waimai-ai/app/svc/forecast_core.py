"""销量预测核心：移动平均 + 星期因子 + 趋势（纯算法，无 LLM）。"""
from datetime import date, timedelta


def forecast_dish(sales: list) -> int:
    """sales: [{"date": "2026-09-25", "qty": 31}, ...] 升序，最多28天。"""
    if not sales:
        return 0
    qtys = [s["qty"] for s in sales]
    if len(qtys) < 7:
        return round(sum(qtys) / len(qtys))

    base = sum(qtys[-7:]) / 7.0
    prev = sum(qtys[-14:-7]) / 7.0 if len(qtys) >= 14 else base

    # 星期因子：目标明天是周几
    target_weekday = (date.today() + timedelta(days=1)).weekday()
    same_weekday_qtys = [
        s["qty"] for s in sales
        if date.fromisoformat(s["date"]).weekday() == target_weekday
    ]
    overall = sum(qtys) / len(qtys)
    weekday_factor = (sum(same_weekday_qtys) / len(same_weekday_qtys) / overall
                      if same_weekday_qtys and overall > 0 else 1.0)

    trend = base / prev if prev > 0 else 1.0
    trend = max(0.8, min(1.2, trend))

    return round(base * weekday_factor * trend)
