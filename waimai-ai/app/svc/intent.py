"""客服意图路由：规则优先，LLM 兜底。"""

ORDER_QUERY_KW = ["订单", "单号", "我的单", "订单状态", "查单"]
URGE_KW = ["催", "快点", "多久", "什么时候", "慢"]
REFUND_KW = ["退款", "退钱", "取消订单", "退单"]
COUPON_KW = ["优惠券", "红包", "满减", "券"]


def route_intent(message: str) -> str:
    """规则命中返回意图；否则返回 OTHER（由后端/上层决定是否 LLM 分类）。"""
    m = message or ""
    if any(k in m for k in ORDER_QUERY_KW):
        return "ORDER_QUERY"
    if any(k in m for k in URGE_KW):
        return "URGE"
    if any(k in m for k in REFUND_KW):
        return "REFUND"
    if any(k in m for k in COUPON_KW):
        return "COUPON"
    return "OTHER"
