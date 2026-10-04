package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.common.exception.BizException;
import com.waimai.common.result.ResultCode;
import com.waimai.common.util.JsonUtil;
import com.waimai.entity.Dish;
import com.waimai.entity.Merchant;
import com.waimai.mapper.DishMapper;
import com.waimai.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车存 Redis Hash：cart:{userId} → field=dishId → value=JSON{merchantId,dishName,image,price,quantity}
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final StringRedisTemplate redis;
    private final DishMapper dishMapper;
    private final MerchantMapper merchantMapper;
    private final UserService userService;
    private final PricingService pricingService;

    /** 单个菜品在购物车里的数量上限（防止误触把数量点到几千） */
    public static final int MAX_QTY = 99;

    private String key(Long userId) { return "cart:" + userId; }

    public Map<String, Object> list(Long userId) {
        var entries = redis.opsForHash().entries(key(userId));
        List<Map<String, Object>> items = new ArrayList<>();
        entries.forEach((k, v) -> items.add(JsonUtil.fromJson((String) v, LinkedHashMap.class)));
        // 按 dishId 升序，保证每次返回顺序一致（Redis Hash 不保证顺序，否则确认订单页商品会乱跳）
        items.sort((a, b) -> Long.compare(((Number) a.get("dishId")).longValue(),
                ((Number) b.get("dishId")).longValue()));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("records", items);
        // 附带商家信息（供商家详情页/确认订单页展示与跨店判断）
        if (!items.isEmpty()) {
            Long merchantId = ((Number) items.get(0).get("merchantId")).longValue();
            Merchant m = merchantMapper.selectById(merchantId);
            if (m != null) {
                resp.put("merchantId", m.getId());
                resp.put("merchantName", m.getShopName());
                resp.put("openStatus", m.getOpenStatus());
                resp.put("minOrderAmount", m.getMinOrderAmount());
                // 配送费按距离动态算；这里还没选收货地址，先给「起步费」作为购物车里的预估
                resp.put("deliveryFee", pricingService.deliveryFee(0));
                resp.put("packageFee", m.getPackageFee() == null ? BigDecimal.ZERO : m.getPackageFee());
            }
        }
        return resp;
    }

    public List<Map<String, Object>> listItems(Long userId) {
        var entries = redis.opsForHash().entries(key(userId));
        List<Map<String, Object>> items = new ArrayList<>();
        entries.forEach((k, v) -> items.add(JsonUtil.fromJson((String) v, LinkedHashMap.class)));
        return items;
    }

    public void add(Long userId, Long dishId, Integer quantity) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null || dish.getStatus() != 1) throw new BizException("菜品不存在或已下架");
        Merchant m = merchantMapper.selectById(dish.getMerchantId());
        if (m == null || m.getOpenStatus() != 1) throw new BizException(ResultCode.SHOP_CLOSED, "店铺已打烊");

        // 跨店校验
        for (Map<String, Object> item : listItems(userId)) {
            if (!String.valueOf(item.get("merchantId")).equals(String.valueOf(dish.getMerchantId()))) {
                throw new BizException(ResultCode.CART_MERCHANT_CONFLICT, "购物车已有其他商家的菜品，请先清空");
            }
        }

        String field = String.valueOf(dishId);
        String raw = (String) redis.opsForHash().get(key(userId), field);
        int add = quantity == null || quantity < 1 ? 1 : quantity;
        int qty = raw == null ? 0 : ((Number) JsonUtil.fromJson(raw, LinkedHashMap.class).get("quantity")).intValue();
        qty = Math.min(qty + add, MAX_QTY);
        writeItem(userId, field, dish, qty);
        // 只有「主动加入」才算兴趣信号；stepper 调数量不算，否则推荐埋点会被稀释
        userService.logBehavior(userId, dish.getMerchantId(), dishId, "CART");
    }

    /**
     * 设置某菜品的数量（绝对数量，不是增量）。
     * quantity<=0 表示移除。
     * <p>注意：前端 stepper 传过来的就是「目标数量」，所以这里必须是 set 语义。
     * 旧实现走的是 /cart/add（增量），导致 stepper 从 1 点到 2 时后端变成 1+2=3，
     * 前端显示 2、实际下单 3，金额对不上。
     */
    public void updateQty(Long userId, Long dishId, Integer quantity) {
        String field = String.valueOf(dishId);
        if (quantity == null || quantity <= 0) {
            redis.opsForHash().delete(key(userId), field);
            return;
        }
        String raw = (String) redis.opsForHash().get(key(userId), field);
        if (raw != null) {
            Dish dish = dishMapper.selectById(dishId);
            if (dish == null || dish.getStatus() != 1) throw new BizException("菜品不存在或已下架");
            writeItem(userId, field, dish, Math.min(quantity, MAX_QTY));
            return;
        }
        // 购物车还没有这道菜：等价于「首次加入 quantity 份」。
        // 旧实现在这里直接 return，导致 stepper 从 0 拉到 1 静默失败。
        add(userId, dishId, quantity);
    }

    /** 写入购物车条目，并顺手刷新名称/图片/价格（商家改价后购物车不会继续用旧价） */
    private void writeItem(Long userId, String field, Dish dish, int qty) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dishId", dish.getId());
        item.put("merchantId", dish.getMerchantId());
        item.put("dishName", dish.getName());
        item.put("image", dish.getImage());
        item.put("price", dish.getPrice());
        item.put("quantity", qty);
        redis.opsForHash().put(key(userId), field, JsonUtil.toJson(item));
    }

    public void clear(Long userId) {
        redis.delete(key(userId));
    }

    /** 移除某商家的全部菜品（下单成功后调用） */
    public void clearMerchant(Long userId, Long merchantId) {
        for (Map<String, Object> item : listItems(userId)) {
            if (String.valueOf(item.get("merchantId")).equals(String.valueOf(merchantId))) {
                redis.opsForHash().delete(key(userId), String.valueOf(item.get("dishId")));
            }
        }
    }
}
