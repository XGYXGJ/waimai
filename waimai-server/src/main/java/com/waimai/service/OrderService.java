package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.waimai.common.exception.BizException;
import com.waimai.common.result.ResultCode;
import com.waimai.common.util.JsonUtil;
import com.waimai.common.util.OrderNoUtil;
import com.waimai.config.RabbitMQConfig;
import com.waimai.dto.WebDTO;
import com.waimai.entity.*;
import com.waimai.mapper.*;
import com.waimai.websocket.WsPusher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 订单核心：状态机 / Redis库存 / MQ超时取消 / 退款回滚。
 * 状态: PENDING_PAYMENT → PAID → ACCEPTED → WAITING_PICKUP → DELIVERING → DELIVERED
 *       终态: CANCELLED / REFUNDED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    public static final String[] VALID_FINAL = {"DELIVERED", "CANCELLED", "REFUNDED"};

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final MerchantMapper merchantMapper;
    private final DishMapper dishMapper;
    private final AddressMapper addressMapper;
    private final UserMapper userMapper;
    private final RiderMapper riderMapper;
    private final UserBehaviorMapper behaviorMapper;
    private final CartService cartService;
    private final CouponService couponService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final WsPusher wsPusher;
    private final OrderNoUtil orderNoUtil;
    private final StringRedisTemplate redis;
    private final RabbitTemplate rabbitTemplate;

    /* ================= 下单 ================= */

    /** 下单预览：金额与可用优惠券 */
    public Map<String, Object> preview(Long userId, Long merchantId) {
        List<Map<String, Object>> items = itemsOfCart(userId, merchantId);
        Merchant m = requireMerchant(merchantId);
        BigDecimal dishAmount = sumDishAmount(items);
        BigDecimal deliveryFee = m.getDeliveryFee();
        BigDecimal discount = BigDecimal.ZERO;
        // 默认选最优券（满足门槛的 maximum discount）
        List<Map<String, Object>> coupons = couponService.usable(userId, merchantId, dishAmount);
        if (!coupons.isEmpty()) discount = (BigDecimal) coupons.get(0).get("discountedAmount");
        BigDecimal pay = dishAmount.add(deliveryFee).subtract(discount).max(BigDecimal.valueOf(0.01));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("items", items);
        resp.put("dishAmount", dishAmount);
        resp.put("deliveryFee", deliveryFee);
        resp.put("packageFee", BigDecimal.ZERO);
        resp.put("discountAmount", discount);
        resp.put("payAmount", pay);
        resp.put("minOrderAmount", m.getMinOrderAmount());
        resp.put("couponOptions", coupons);
        return resp;
    }

    @Transactional
    public Map<String, Object> create(Long userId, WebDTO.OrderCreateReq req) {
        Merchant m = requireMerchant(req.getMerchantId());
        if (m.getOpenStatus() != 1) throw new BizException(ResultCode.SHOP_CLOSED, "店铺已打烊");

        List<Map<String, Object>> items = itemsOfCart(userId, req.getMerchantId());
        if (items.isEmpty()) throw new BizException("购物车为空");

        Address addr = addressMapper.selectById(req.getAddressId());
        if (addr == null || !addr.getUserId().equals(userId)) throw new BizException("收货地址无效");

        BigDecimal dishAmount = sumDishAmount(items);
        if (dishAmount.compareTo(m.getMinOrderAmount()) < 0) {
            throw new BizException("未达到起送价 " + m.getMinOrderAmount() + " 元");
        }

        // ---- Redis Lua 扣库存（失败回滚已扣项） ----
        List<Long> deducted = new ArrayList<>();
        for (Map<String, Object> item : items) {
            long dishId = ((Number) item.get("dishId")).longValue();
            int qty = ((Number) item.get("quantity")).intValue();
            long r = deductStock(dishId, qty);
            if (r != 0) {
                for (Long d : deducted) {
                    restoreStock(d, qtyOf(items, d));
                }
                throw new BizException(ResultCode.STOCK_NOT_ENOUGH,
                        "「" + item.get("dishName") + "」库存不足");
            }
            deducted.add(dishId);
        }

        // ---- 优惠券校验 ----
        BigDecimal discount = BigDecimal.ZERO;
        if (req.getUserCouponId() != null) {
            UserCouponMeta meta = loadCoupon(req.getUserCouponId(), userId, m.getId(), dishAmount);
            discount = meta.discount;
        }

        BigDecimal deliveryFee = m.getDeliveryFee();
        BigDecimal pay = dishAmount.add(deliveryFee).subtract(discount).max(BigDecimal.valueOf(0.01));

        // ---- 落库 ----
        Orders order = new Orders();
        order.setOrderNo(orderNoUtil.next());
        order.setUserId(userId);
        order.setMerchantId(m.getId());
        order.setAddressSnapshot(JsonUtil.toJson(Map.of(
                "contact", addr.getContact(), "phone", addr.getPhone(),
                "detail", addr.getProvince() + addr.getCity() + addr.getDistrict() + addr.getDetail(),
                "lng", addr.getLng(), "lat", addr.getLat())));
        order.setDishAmount(dishAmount);
        order.setDeliveryFee(deliveryFee);
        order.setPackageFee(BigDecimal.ZERO);
        order.setDiscountAmount(discount);
        order.setPayAmount(pay);
        order.setUserCouponId(req.getUserCouponId());
        order.setStatus("PENDING_PAYMENT");
        order.setRemark(req.getRemark());
        ordersMapper.insert(order);

        for (Map<String, Object> item : items) {
            OrderItem oi = new OrderItem();
            oi.setOrderId(order.getId());
            oi.setDishId(((Number) item.get("dishId")).longValue());
            oi.setDishName((String) item.get("dishName"));
            oi.setImage((String) item.get("image"));
            oi.setPrice(new BigDecimal(String.valueOf(item.get("price"))));
            oi.setQuantity(((Number) item.get("quantity")).intValue());
            orderItemMapper.insert(oi);

            // 行为埋点
            UserBehavior b = new UserBehavior();
            b.setUserId(userId);
            b.setMerchantId(m.getId());
            b.setDishId(oi.getDishId());
            b.setAction("ORDER");
            behaviorMapper.insert(b);
        }

        logStatus(order.getId(), null, "PENDING_PAYMENT", "user:" + userId);

        if (req.getUserCouponId() != null) {
            couponService.useCoupon(req.getUserCouponId(), order.getId());
        }

        // ---- MQ 延迟消息（15分钟未支付自动取消） ----
        rabbitTemplate.convertAndSend(RabbitMQConfig.DELAY_EXCHANGE,
                RabbitMQConfig.DELAY_ROUTING_KEY, String.valueOf(order.getId()));

        cartService.clearMerchant(userId, m.getId());
        return Map.of("orderId", order.getId(), "orderNo", order.getOrderNo(), "payAmount", pay);
    }

    /** 模拟支付：PENDING_PAYMENT → PAID */
    @Transactional
    public void pay(Long userId, Long orderId) {
        Orders order = requireOwned(orderId, userId);
        if (!transit(orderId, "PENDING_PAYMENT", "PAID", "user:" + userId)) {
            throw new BizException(ResultCode.ORDER_STATUS_CHANGED, "订单状态已变更（可能已超时取消）");
        }
        Orders upd = new Orders();
        upd.setId(orderId);
        upd.setPayTime(LocalDateTime.now());
        ordersMapper.updateById(upd);

        Merchant m = merchantMapper.selectById(order.getMerchantId());
        wsPusher.pushNewOrder(m.getUserId(), orderId, order.getPayAmount().doubleValue());
        wsPusher.pushOrderStatus(userId, orderId, order.getOrderNo(), "PENDING_PAYMENT", "PAID");
        notificationService.notify(m.getUserId(), "ORDER_STATUS", "新订单",
                "您有一笔新订单 " + order.getOrderNo() + "，实付 " + order.getPayAmount() + " 元");
    }

    /** 用户取消：待支付→CANCELLED；已支付未接单→REFUNDED（自动全额退款） */
    @Transactional
    public void cancelByUser(Long userId, Long orderId) {
        Orders order = requireOwned(orderId, userId);
        String from = order.getStatus();
        if ("PENDING_PAYMENT".equals(from)) {
            if (!transit(orderId, "PENDING_PAYMENT", "CANCELLED", "user:" + userId)) {
                throw new BizException(ResultCode.ORDER_STATUS_CHANGED, "订单状态已变更");
            }
            markCancel(orderId, "用户取消", "USER");
        } else if ("PAID".equals(from)) {
            if (!transit(orderId, "PAID", "REFUNDED", "user:" + userId)) {
                throw new BizException(ResultCode.ORDER_STATUS_CHANGED, "商家已接单，无法取消");
            }
            markCancel(orderId, "用户取消（自动退款）", "USER");
        } else {
            throw new BizException("当前状态不可取消");
        }
        rollback(order);
        Merchant m = merchantMapper.selectById(order.getMerchantId());
        wsPusher.pushOrderStatus(userId, orderId, order.getOrderNo(), from, "CANCELLED".equals(getStatus(orderId)) ? "CANCELLED" : "REFUNDED");
        if (m != null) notificationService.notify(m.getUserId(), "ORDER_STATUS", "订单取消",
                "订单 " + order.getOrderNo() + " 已被用户取消");
    }

    /** MQ 死信：超时未支付自动取消 */
    @Transactional
    public void cancelOnTimeout(Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null || !"PENDING_PAYMENT".equals(order.getStatus())) return;
        if (!transit(orderId, "PENDING_PAYMENT", "CANCELLED", "SYSTEM")) return;
        markCancel(orderId, "超时未支付，系统自动取消", "SYSTEM");
        rollback(order);
        wsPusher.pushOrderStatus(order.getUserId(), orderId, order.getOrderNo(), "PENDING_PAYMENT", "CANCELLED");
        notificationService.notify(order.getUserId(), "ORDER_STATUS", "订单已取消",
                "订单 " + order.getOrderNo() + " 超时未支付已自动取消");
        log.info("order {} auto cancelled by timeout", orderId);
    }

    /* ================= 商户侧 ================= */

    @Transactional
    public void accept(Long merchantUserId, Long orderId) {
        Orders order = requireMerchantOrder(merchantUserId, orderId);
        if (!transit(orderId, "PAID", "ACCEPTED", "merchant:" + merchantUserId)) {
            throw new BizException(ResultCode.ORDER_STATUS_CHANGED, "订单状态已变更");
        }
        Orders upd = new Orders();
        upd.setId(orderId);
        upd.setAcceptTime(LocalDateTime.now());
        ordersMapper.updateById(upd);
        wsPusher.pushOrderStatus(order.getUserId(), orderId, order.getOrderNo(), "PAID", "ACCEPTED");
        notificationService.notify(order.getUserId(), "ORDER_STATUS", "商家已接单",
                "订单 " + order.getOrderNo() + " 商家已接单，正在备餐");
    }

    @Transactional
    public void reject(Long merchantUserId, Long orderId, String reason) {
        Orders order = requireMerchantOrder(merchantUserId, orderId);
        if (!transit(orderId, "PAID", "REFUNDED", "merchant:" + merchantUserId)) {
            throw new BizException(ResultCode.ORDER_STATUS_CHANGED, "订单状态已变更");
        }
        markCancel(orderId, "商家拒单：" + (reason == null ? "" : reason) + "（自动退款）", "MERCHANT");
        rollback(order);
        wsPusher.pushOrderStatus(order.getUserId(), orderId, order.getOrderNo(), "PAID", "REFUNDED");
        notificationService.notify(order.getUserId(), "ORDER_STATUS", "订单已退款",
                "很抱歉，订单 " + order.getOrderNo() + " 商家已拒单，支付金额将自动退回");
    }

    /** 备餐完成：ACCEPTED → WAITING_PICKUP（骑手可抢单） */
    @Transactional
    public void ready(Long merchantUserId, Long orderId) {
        Orders order = requireMerchantOrder(merchantUserId, orderId);
        if (!transit(orderId, "ACCEPTED", "WAITING_PICKUP", "merchant:" + merchantUserId)) {
            throw new BizException(ResultCode.ORDER_STATUS_CHANGED, "订单状态已变更");
        }
        wsPusher.pushOrderStatus(order.getUserId(), orderId, order.getOrderNo(), "ACCEPTED", "WAITING_PICKUP");
    }

    /* ================= 查询 ================= */

    public Map<String, Object> myOrders(Long userId, String status, int page, int size) {
        QueryWrapper<Orders> qw = new QueryWrapper<Orders>().eq("user_id", userId)
                .eq(status != null && !status.isBlank(), "status", status)
                .orderByDesc("created_at");
        return pageWithDetail(qw, page, size);
    }

    public Map<String, Object> merchantOrders(Long merchantUserId, String status, int page, int size) {
        Merchant m = merchantOfUser(merchantUserId);
        QueryWrapper<Orders> qw = new QueryWrapper<Orders>().eq("merchant_id", m.getId())
                .eq(status != null && !status.isBlank(), "status", status)
                .orderByDesc("created_at");
        return pageWithDetail(qw, page, size);
    }

    public Map<String, Object> detail(Long userId, Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        // 越权校验：用户本人 / 商家本人 / 骑手本人 / 管理员
        User u = userMapper.selectById(userId);
        boolean ok = order.getUserId().equals(userId)
                || ("MERCHANT".equals(u.getRole()) && mOf(u.getId()) != null && mOf(u.getId()).getId().equals(order.getMerchantId()))
                || ("RIDER".equals(u.getRole()) && order.getRiderId() != null
                    && riderOfUser(userId) != null && order.getRiderId().equals(riderOfUser(userId).getId()))
                || "ADMIN".equals(u.getRole());
        if (!ok) throw new BizException(ResultCode.FORBIDDEN, "无权查看该订单");

        Map<String, Object> vo = toVo(order);
        vo.put("items", orderItemMapper.selectList(new QueryWrapper<OrderItem>().eq("order_id", orderId)));
        vo.put("statusLogs", statusLogMapper.selectList(
                new QueryWrapper<OrderStatusLog>().eq("order_id", orderId).orderByAsc("created_at")));
        Merchant m = merchantMapper.selectById(order.getMerchantId());
        if (m != null) vo.put("merchantName", m.getShopName());
        vo.put("address", JsonUtil.fromJson(order.getAddressSnapshot(), Map.class));
        if (order.getRiderId() != null) {
            Rider r = riderMapper.selectById(order.getRiderId());
            if (r != null) {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("realName", r.getRealName());
                rm.put("phone", r.getPhone());
                vo.put("rider", rm);
            }
        }
        return vo;
    }

    /* ================= 骑手侧状态推进（由 RiderService 调用） ================= */

    public void grabOrder(Long orderId, Long riderId) {
        // 乐观条件更新防并发抢单
        int rows = ordersMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Orders>()
                        .set("rider_id", riderId)
                        .eq("id", orderId)
                        .isNull("rider_id")
                        .in("status", "ACCEPTED", "WAITING_PICKUP"));
        if (rows == 0) throw new BizException(ResultCode.GRAB_FAILED, "手慢了，订单已被其他骑手接取");
        Orders order = ordersMapper.selectById(orderId);
        logStatus(orderId, order.getStatus(), "WAITING_PICKUP", "rider:" + riderId);
        if (!"WAITING_PICKUP".equals(order.getStatus())) {
            transit(orderId, order.getStatus(), "WAITING_PICKUP", "rider:" + riderId);
        }
        wsPusher.pushOrderStatus(order.getUserId(), orderId, order.getOrderNo(), order.getStatus(), "WAITING_PICKUP");
        notificationService.notify(order.getUserId(), "ORDER_STATUS", "骑手已接单",
                "订单 " + order.getOrderNo() + " 已有骑手接单");
    }

    public void pickup(Long orderId, Long riderId) {
        Orders order = requireRiderOrder(orderId, riderId);
        if (!transit(orderId, "WAITING_PICKUP", "DELIVERING", "rider:" + riderId)) {
            throw new BizException(ResultCode.ORDER_STATUS_CHANGED, "订单状态已变更");
        }
        Orders upd = new Orders();
        upd.setId(orderId);
        upd.setPickupTime(LocalDateTime.now());
        ordersMapper.updateById(upd);
        wsPusher.pushOrderStatus(order.getUserId(), orderId, order.getOrderNo(), "WAITING_PICKUP", "DELIVERING");
    }

    public void deliver(Long orderId, Long riderId) {
        Orders order = requireRiderOrder(orderId, riderId);
        if (!transit(orderId, "DELIVERING", "DELIVERED", "rider:" + riderId)) {
            throw new BizException(ResultCode.ORDER_STATUS_CHANGED, "订单状态已变更");
        }
        Orders upd = new Orders();
        upd.setId(orderId);
        upd.setDeliveredTime(LocalDateTime.now());
        ordersMapper.updateById(upd);
        // 销量累计
        Merchant m = merchantMapper.selectById(order.getMerchantId());
        if (m != null) {
            m.setMonthlySales(m.getMonthlySales() + 1);
            merchantMapper.updateById(m);
        }
        for (OrderItem oi : orderItemMapper.selectList(new QueryWrapper<OrderItem>().eq("order_id", orderId))) {
            Dish d = dishMapper.selectById(oi.getDishId());
            if (d != null) {
                d.setMonthlySales(d.getMonthlySales() + oi.getQuantity());
                dishMapper.updateById(d);
            }
        }
        wsPusher.pushOrderStatus(order.getUserId(), orderId, order.getOrderNo(), "DELIVERING", "DELIVERED");
        notificationService.notify(order.getUserId(), "ORDER_STATUS", "订单已送达",
                "订单 " + order.getOrderNo() + " 已送达，欢迎评价");
    }

    /* ================= 内部工具 ================= */

    /** 库存 Lua：返回0成功；-1不足；-2缓存未初始化（先加载再重试） */
    private static final DefaultRedisScript<Long> STOCK_SCRIPT = new DefaultRedisScript<>(
            "local s = tonumber(redis.call('GET', KEYS[1])) " +
            "if s == nil then return -2 end " +
            "if s < tonumber(ARGV[1]) then return -1 end " +
            "redis.call('DECRBY', KEYS[1], ARGV[1]) return 0", Long.class);

    private long deductStock(long dishId, int qty) {
        String key = "dish:stock:" + dishId;
        Long r = redis.execute(STOCK_SCRIPT, List.of(key), String.valueOf(qty));
        if (r != null && r == -2) {
            Dish d = dishMapper.selectById(dishId);
            if (d == null) return -1;
            redis.opsForValue().set(key, String.valueOf(d.getStock()));
            r = redis.execute(STOCK_SCRIPT, List.of(key), String.valueOf(qty));
        }
        return r == null ? -1 : r;
    }

    public void restoreStock(long dishId, int qty) {
        redis.opsForValue().increment("dish:stock:" + dishId, qty);
    }

    /** 取消/拒单/退款统一回滚：回补库存 + 释放优惠券 */
    private void rollback(Orders order) {
        for (OrderItem oi : orderItemMapper.selectList(new QueryWrapper<OrderItem>().eq("order_id", order.getId()))) {
            restoreStock(oi.getDishId(), oi.getQuantity());
        }
        if (order.getUserCouponId() != null) couponService.revertCoupon(order.getUserCouponId());
    }

    private void markCancel(Long orderId, String reason, String by) {
        Orders upd = new Orders();
        upd.setId(orderId);
        upd.setCancelReason(reason);
        upd.setCancelBy(by);
        ordersMapper.updateById(upd);
    }

    /** 条件状态迁移（乐观锁），成功返回 true */
    private boolean transit(Long orderId, String from, String to, String operator) {
        int rows = ordersMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Orders>()
                        .set("status", to)
                        .eq("id", orderId)
                        .eq("status", from));
        if (rows > 0) {
            logStatus(orderId, from, to, operator);
            return true;
        }
        return false;
    }

    private void logStatus(Long orderId, String from, String to, String operator) {
        OrderStatusLog logRow = new OrderStatusLog();
        logRow.setOrderId(orderId);
        logRow.setFromStatus(from);
        logRow.setToStatus(to);
        logRow.setOperator(operator);
        statusLogMapper.insert(logRow);
    }

    private String getStatus(Long orderId) {
        return ordersMapper.selectById(orderId).getStatus();
    }

    private List<Map<String, Object>> itemsOfCart(Long userId, Long merchantId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : cartService.listItems(userId)) {
            if (String.valueOf(item.get("merchantId")).equals(String.valueOf(merchantId))) {
                result.add(item);
            }
        }
        return result;
    }

    private int qtyOf(List<Map<String, Object>> items, Long dishId) {
        for (Map<String, Object> item : items) {
            if (((Number) item.get("dishId")).longValue() == dishId) {
                return ((Number) item.get("quantity")).intValue();
            }
        }
        return 1;
    }

    private BigDecimal sumDishAmount(List<Map<String, Object>> items) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            BigDecimal price = new BigDecimal(String.valueOf(item.get("price")));
            sum = sum.add(price.multiply(BigDecimal.valueOf(((Number) item.get("quantity")).intValue())));
        }
        return sum;
    }

    private record UserCouponMeta(BigDecimal discount) { }

    private UserCouponMeta loadCoupon(Long userCouponId, Long userId, Long merchantId, BigDecimal dishAmount) {
        var ucs = couponService.usable(userId, merchantId, dishAmount);
        for (Map<String, Object> c : ucs) {
            if (String.valueOf(c.get("userCouponId")).equals(String.valueOf(userCouponId))) {
                return new UserCouponMeta((BigDecimal) c.get("discountedAmount"));
            }
        }
        throw new BizException(ResultCode.COUPON_INVALID, "优惠券不可用");
    }

    private Merchant requireMerchant(Long merchantId) {
        Merchant m = merchantMapper.selectById(merchantId);
        if (m == null || m.getAuditStatus() != 1) throw new BizException("商家不存在或未过审");
        return m;
    }

    private Merchant merchantOfUser(Long merchantUserId) {
        Merchant m = mOf(merchantUserId);
        if (m == null) throw new BizException(ResultCode.FORBIDDEN, "非商户账号");
        return m;
    }

    private Merchant mOf(Long userId) {
        return merchantMapper.selectOne(new QueryWrapper<Merchant>().eq("user_id", userId));
    }

    private Rider riderOfUser(Long userId) {
        return riderMapper.selectOne(new QueryWrapper<Rider>().eq("user_id", userId));
    }

    private Orders requireOwned(Long orderId, Long userId) {
        Orders o = ordersMapper.selectById(orderId);
        if (o == null || !o.getUserId().equals(userId)) throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        return o;
    }

    private Orders requireMerchantOrder(Long merchantUserId, Long orderId) {
        Merchant m = merchantOfUser(merchantUserId);
        Orders o = ordersMapper.selectById(orderId);
        if (o == null || !o.getMerchantId().equals(m.getId())) throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        return o;
    }

    private Orders requireRiderOrder(Long orderId, Long riderId) {
        Orders o = ordersMapper.selectById(orderId);
        if (o == null || o.getRiderId() == null || !o.getRiderId().equals(riderId)) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return o;
    }

    private Map<String, Object> pageWithDetail(QueryWrapper<Orders> qw, int page, int size) {
        Page<Orders> p = ordersMapper.selectPage(new Page<>(page, size), qw);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Orders o : p.getRecords()) {
            Map<String, Object> vo = toVo(o);
            vo.put("items", orderItemMapper.selectList(new QueryWrapper<OrderItem>().eq("order_id", o.getId())));
            records.add(vo);
        }
        return Map.of("records", records, "total", p.getTotal(), "pages", p.getPages());
    }

    private Map<String, Object> toVo(Orders o) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", o.getId());
        vo.put("orderNo", o.getOrderNo());
        vo.put("userId", o.getUserId());
        vo.put("merchantId", o.getMerchantId());
        vo.put("riderId", o.getRiderId());
        vo.put("dishAmount", o.getDishAmount());
        vo.put("deliveryFee", o.getDeliveryFee());
        vo.put("discountAmount", o.getDiscountAmount());
        vo.put("payAmount", o.getPayAmount());
        vo.put("status", o.getStatus());
        vo.put("remark", o.getRemark());
        vo.put("payTime", o.getPayTime());
        vo.put("acceptTime", o.getAcceptTime());
        vo.put("pickupTime", o.getPickupTime());
        vo.put("deliveredTime", o.getDeliveredTime());
        vo.put("cancelReason", o.getCancelReason());
        vo.put("createdAt", o.getCreatedAt());
        return vo;
    }
}
