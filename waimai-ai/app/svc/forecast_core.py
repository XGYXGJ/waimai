"""销量预测核心：移动平均 + 星期因子 + 趋势（纯算法，无 LLM）。"""
from datetime import date, timedelta


def _qty(s) -> float:
    """兼容两种入参：{"date": "...", "qty": 31} 或直接一个数字。"""
    v = s.get("qty", s.get("quantity")) if isinstance(s, dict) else s
    try:
        return float(v or 0)
    except (TypeError, ValueError):
        return 0.0


def _weekday(s):
    """取该条数据的星期几；没有日期字段时返回 None（此时不计入星期因子）。"""
    if not isinstance(s, dict):
        return None
    raw = s.get("date") or s.get("stat_date")
    if not raw:
        return None
    try:
        return date.fromisoformat(str(raw)[:10]).weekday()
    except ValueError:
        return None


def forecast_dish(sales: list) -> int:
    """sales: [{"date": "2026-09-25", "qty": 31}, ...] 升序，最多28天。

    也兼容纯数字数组，并且对缺字段/脏数据按 0 处理：
    预测是给商家看的辅助信息，不该因为一条脏数据让整个接口 500。
    """
    if not sales:
        return 0
    qtys = [_qty(s) for s in sales]
    if len(qtys) < 7:
        return round(sum(qtys) / len(qtys))

    base = sum(qtys[-7:]) / 7.0
    prev = sum(qtys[-14:-7]) / 7.0 if len(qtys) >= 14 else base

    # 星期因子：目标明天是周几
    target_weekday = (date.today() + timedelta(days=1)).weekday()
    same_weekday_qtys = [q for s, q in zip(sales, qtys) if _weekday(s) == target_weekday]
    overall = sum(qtys) / len(qtys)
    weekday_factor = (sum(same_weekday_qtys) / len(same_weekday_qtys) / overall
                      if same_weekday_qtys and overall > 0 else 1.0)

    trend = base / prev if prev > 0 else 1.0
    trend = max(0.8, min(1.2, trend))

    return round(base * weekday_factor * trend)
