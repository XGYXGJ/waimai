# -*- coding: utf-8 -*-
"""生成 MyBatis-Plus 实体类与 Mapper 接口（一次性工具）"""
import os

BASE = r"E:\BiSHe\waimai\waimai-server\src\main\java\com\waimai"
ENT = os.path.join(BASE, "entity")
MAP = os.path.join(BASE, "mapper")

# (类名, 表名, [(字段名, java类型)], 主键特殊配置)
T = "java.time.LocalDateTime"; D = "java.time.LocalDate"; BD = "java.math.BigDecimal"
tables = [
    ("User", "user", [("id","Long"),("phone","String"),("passwordHash","String"),("nickname","String"),("avatar","String"),("role","String"),("status","Integer"),("createdAt",T),("updatedAt",T)]),
    ("Address", "address", [("id","Long"),("userId","Long"),("contact","String"),("phone","String"),("gender","Integer"),("province","String"),("city","String"),("district","String"),("detail","String"),("lng",BD),("lat",BD),("tag","String"),("isDefault","Integer"),("createdAt",T)]),
    ("Favorite", "favorite", [("id","Long"),("userId","Long"),("merchantId","Long"),("createdAt",T)]),
    ("SearchHistory", "search_history", [("id","Long"),("userId","Long"),("keyword","String"),("createdAt",T)]),
    ("MerchantCategory", "merchant_category", [("id","Integer"),("name","String"),("icon","String"),("sort","Integer")]),
    ("Merchant", "merchant", [("id","Long"),("userId","Long"),("shopName","String"),("categoryId","Integer"),("logo","String"),("cover","String"),("notice","String"),("phone","String"),("address","String"),("lng",BD),("lat",BD),("businessHours","String"),("minOrderAmount",BD),("deliveryFee",BD),("rating",BD),("monthlySales","Integer"),("openStatus","Integer"),("auditStatus","Integer"),("auditRemark","String"),("createdAt",T)]),
    ("DishCategory", "dish_category", [("id","Long"),("merchantId","Long"),("name","String"),("sort","Integer")]),
    ("Dish", "dish", [("id","Long"),("merchantId","Long"),("categoryId","Long"),("name","String"),("description","String"),("image","String"),("price",BD),("originalPrice",BD),("unit","String"),("stock","Integer"),("monthlySales","Integer"),("rating",BD),("tags","String"),("isRecommend","Integer"),("status","Integer"),("createdAt",T)]),
    ("ShoppingCart", "shopping_cart", [("id","Long"),("userId","Long"),("merchantId","Long"),("dishId","Long"),("dishName","String"),("image","String"),("price",BD),("quantity","Integer"),("createdAt",T)]),
    ("Coupon", "coupon", [("id","Long"),("merchantId","Long"),("name","String"),("type","Integer"),("thresholdAmount",BD),("discountAmount",BD),("discountRate",BD),("totalCount","Integer"),("receivedCount","Integer"),("perUserLimit","Integer"),("startTime",T),("endTime",T),("status","Integer"),("createdAt",T)]),
    ("UserCoupon", "user_coupon", [("id","Long"),("couponId","Long"),("userId","Long"),("status","Integer"),("receivedAt",T),("usedAt",T),("orderId","Long")]),
    ("Orders", "orders", [("id","Long"),("orderNo","String"),("userId","Long"),("merchantId","Long"),("riderId","Long"),("addressSnapshot","String"),("dishAmount",BD),("deliveryFee",BD),("packageFee",BD),("discountAmount",BD),("payAmount",BD),("userCouponId","Long"),("status","String"),("remark","String"),("payTime",T),("acceptTime",T),("pickupTime",T),("deliveredTime",T),("cancelReason","String"),("cancelBy","String"),("createdAt",T),("updatedAt",T)]),
    ("OrderItem", "order_item", [("id","Long"),("orderId","Long"),("dishId","Long"),("dishName","String"),("image","String"),("price",BD),("quantity","Integer")]),
    ("OrderStatusLog", "order_status_log", [("id","Long"),("orderId","Long"),("fromStatus","String"),("toStatus","String"),("operator","String"),("createdAt",T)]),
    ("Rider", "rider", [("id","Long"),("userId","Long"),("realName","String"),("phone","String"),("vehicle","String"),("auditStatus","Integer"),("workStatus","Integer"),("todayOrders","Integer"),("createdAt",T)]),
    ("RiderLocation", "rider_location", [("id","Long"),("riderId","Long"),("orderId","Long"),("lng",BD),("lat",BD),("createdAt",T)]),
    ("Review", "review", [("id","Long"),("orderId","Long"),("userId","Long"),("merchantId","Long"),("rating","Integer"),("content","String"),("images","String"),("sentiment","String"),("sentimentScore",BD),("reply","String"),("createdAt",T)]),
    ("BidCampaign", "bid_campaign", [("id","Long"),("merchantId","Long"),("keyword","String"),("bid",BD),("dailyBudget",BD),("todaySpent",BD),("status","Integer"),("startDate",D),("endDate",D),("createdAt",T)]),
    ("BidLog", "bid_log", [("id","Long"),("campaignId","Long"),("userId","Long"),("position","Integer"),("clicked","Integer"),("cost",BD),("createdAt",T)]),
    ("UserBehavior", "user_behavior", [("id","Long"),("userId","Long"),("merchantId","Long"),("dishId","Long"),("action","String"),("createdAt",T)]),
    ("RecommendLog", "recommend_log", [("id","Long"),("userId","Long"),("dishId","Long"),("merchantId","Long"),("source","String"),("score",BD),("isExposed","Integer"),("isClicked","Integer"),("isOrdered","Integer"),("reason","String"),("createdAt",T)]),
    ("DishSalesDaily", "dish_sales_daily", [("id","Long"),("merchantId","Long"),("dishId","Long"),("statDate",D),("quantity","Integer"),("amount",BD)]),
    ("ChatSession", "chat_session", [("id","Long"),("userId","Long"),("title","String"),("createdAt",T)]),
    ("ChatMessage", "chat_message", [("id","Long"),("sessionId","Long"),("role","String"),("content","String"),("createdAt",T)]),
    ("Notification", "notification", [("id","Long"),("userId","Long"),("type","String"),("title","String"),("content","String"),("isRead","Integer"),("createdAt",T)]),
    ("SysConfig", "sys_config", [("configKey","String"),("configValue","String"),("description","String")]),
]

def gen_entity(cls, table, fields):
    # 收集 import
    imports = set()
    for _, jt in fields:
        if jt in (T, D, BD):
            imports.add(jt)
    lines = []
    lines.append("package com.waimai.entity;")
    lines.append("")
    if imports:
        for imp in sorted(imports):
            lines.append(f"import {imp};")
        lines.append("")
    lines.append("import com.baomidou.mybatisplus.annotation.IdType;")
    lines.append("import com.baomidou.mybatisplus.annotation.TableId;")
    lines.append("import com.baomidou.mybatisplus.annotation.TableName;")
    lines.append("import lombok.Data;")
    lines.append("")
    lines.append("@Data")
    lines.append(f'@TableName("{table}")')
    lines.append(f"public class {cls} {{")
    first = fields[0]
    if cls == "SysConfig":
        lines.append(f'    @TableId(value = "config_key", type = IdType.INPUT)')
    else:
        lines.append(f'    @TableId(value = "id", type = IdType.AUTO)')
    for name, jt in fields:
        jn = jt.split(".")[-1]
        lines.append(f"    private {jn} {name};")
        if name != fields[-1][0]:
            lines.append("")
    lines.append("}")
    return "\n".join(lines)

def gen_mapper(cls):
    return f"""package com.waimai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.waimai.entity.{cls};

public interface {cls}Mapper extends BaseMapper<{cls}> {{
}}
"""

os.makedirs(ENT, exist_ok=True)
os.makedirs(MAP, exist_ok=True)
for cls, table, fields in tables:
    with open(os.path.join(ENT, f"{cls}.java"), "w", encoding="utf-8") as f:
        f.write(gen_entity(cls, table, fields))
    with open(os.path.join(MAP, f"{cls}Mapper.java"), "w", encoding="utf-8") as f:
        f.write(gen_mapper(cls))
print(f"generated {len(tables)} entities + {len(tabs := tables)} mappers")
