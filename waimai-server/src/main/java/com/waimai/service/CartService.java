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

    private String key(Long userId) { return "cart:" + userId; }

    public Map<String, Object> list(Long userId) {
        var entries = redis.opsForHash().entries(key(userId));
        List<Map<String, Object>> items = new ArrayList<>();
        entries.forEach((k, v) -> items.add(JsonUtil.fromJson((String) v, LinkedHashMap.class)));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("records", items);
        // 附带商家配送费/打包费（供确认订单页展示）
        if (!items.isEmpty()) {
            Long merchantId = ((Number) items.get(0).get("merchantId")).longValue();
            Merchant m = merchantMapper.selectById(merchantId);
            if (m != null) {
                resp.put("merchantId", m.getId());
                resp.put("deliveryFee", m.getDeliveryFee());
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
        int qty = quantity == null || quantity < 1 ? 1 : quantity;
        if (raw != null) {
            Map<String, Object> item = JsonUtil.fromJson(raw, LinkedHashMap.class);
            qty += ((Number) item.get("quantity")).intValue();
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dishId", dishId);
        item.put("merchantId", dish.getMerchantId());
        item.put("dishName", dish.getName());
        item.put("image", dish.getImage());
        item.put("price", dish.getPrice());
        item.put("quantity", qty);
        redis.opsForHash().put(key(userId), field, JsonUtil.toJson(item));
        userService.logBehavior(userId, dish.getMerchantId(), dishId, "CART");
    }

    public void updateQty(Long userId, Long dishId, Integer quantity) {
        String field = String.valueOf(dishId);
        if (quantity == null || quantity <= 0) {
            redis.opsForHash().delete(key(userId), field);
            return;
        }
        String raw = (String) redis.opsForHash().get(key(userId), field);
        if (raw == null) return;
        Map<String, Object> item = JsonUtil.fromJson(raw, LinkedHashMap.class);
        item.put("quantity", quantity);
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
