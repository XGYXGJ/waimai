package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.waimai.common.exception.BizException;
import com.waimai.common.result.ResultCode;
import com.waimai.common.util.GeoUtil;
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
import java.math.RoundingMode;
import java.time.Duration;
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
    private final ReviewMapper reviewMapper;
    private final CartService cartService;
    private final CouponService couponService;
    private final UserService userService;
    private final PricingService pricingService;
    private final NotificationService notificationService;
    private final WsPusher wsPusher;
    private final OrderNoUtil orderNoUtil;
    private final StringRedisTemplate redis;
    private final RabbitTemplate rabbitTemplate;

    /* ================= 下单 ================= */

    /**
     * 下单预览：金额、按距离动态计算的配送费、可用优惠券、是否在配送范围内。
     *
     * @param addressId 用户当前选中的收货地址；为空时退回其默认地址。
     *                  配送费依赖「商家 → 地址」的距离，所以地址一变金额就要重算。
     */
    public Map<String, Object> preview(Long userId, Long merchantId, Long addressId) {
        List<Map<String, Object>> items = itemsOfCart(userId, merchantId);
        Merchant m = requireMerchant(merchantId);
        // 补上库存/上架状态：购物车里的菜可能已经下架或库存不足，
        // 确认订单页需要据此禁用 stepper、提前提示，而不是等下单才报错。
        for (Map<String, Object> it : items) {
            Dish d = dishMapper.selectById(((Number) it.get("dishId")).longValue());
            if (d != null) {
                it.put("stock", d.getStock());
                it.put("dishStatus", d.getStatus());
            }
        }
        BigDecimal dishAmount = sumDishAmount(items);

        // ---- 配送费：由距离动态算出，不再是商家写死的值 ----
        Address addr = resolveAddress(userId, addressId);
        Double distanceKm = distanceOf(m, addr);
        BigDecimal deliveryFee = pricingService.deliveryFee(distanceKm == null ? 0 : distanceKm);
        double radius = pricingService.radiusOf(m.getDeliveryRadiusKm());
        boolean inRange = pricingService.inRange(m.getDeliveryRadiusKm(), distanceKm);

        BigDecimal packageFee = m.getPackageFee() == null ? BigDecimal.ZERO : m.getPackageFee();
        BigDecimal discount = BigDecimal.ZERO;
        // 默认选最优券（满足门槛的 maximum discount）
        List<Map<String, Object>> coupons = couponService.usable(userId, merchantId, dishAmount);
        if (!coupons.isEmpty()) discount = (BigDecimal) coupons.get(0).get("discountedAmount");
        BigDecimal pay = dishAmount.add(deliveryFee).add(packageFee).subtract(discount).max(BigDecimal.valueOf(0.01));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("items", items);
        resp.put("merchantId", m.getId());
        resp.put("merchantName", m.getShopName());
        resp.put("openStatus", m.getOpenStatus());
        resp.put("dishAmount", dishAmount);
        resp.put("deliveryFee", deliveryFee);
        resp.put("packageFee", packageFee);
        resp.put("discountAmount", discount);
        resp.put("payAmount", pay);
        resp.put("minOrderAmount", m.getMinOrderAmount());
        resp.put("couponOptions", coupons);
        // 距离/范围：确认订单页据此提示「超出配送范围」，避免下单才被拒
        resp.put("hasAddress", addr != null);
        resp.put("distanceKm", distanceKm == null ? null : round(distanceKm, 3));
        resp.put("deliveryRadiusKm", radius);
        resp.put("inRange", inRange);
        if (!inRange) {
            resp.put("rangeTip", "收货地址距商家约 " + String.format("%.1f", distanceKm)
                    + "km，超出该商家的 " + trim(radius) + "km 配送范围");
        }
        return resp;
    }

    @Transactional
    public Map<String, Object> create(Long userId, WebDTO.OrderCreateReq req) {
        // ================= 幂等（防连点 / 弱网重试产生两笔订单） =================
        // 三层保护：
        //   1. client_token 唯一索引  → 数据层面绝对不会重复（最终防线）
        //   2. 先按 token 反查已有订单 → 顺序重试直接返回原单（最常见路径）
        //   3. Redis 占位锁            → 并发同 token 时挡住第二个请求，避免撞唯一键
        String token = normalizeToken(req.getClientToken());
        if (token != null) {
            Orders exist = findByIdemToken(userId, token);
            if (exist != null) {
                log.info("order idempotent hit: user={} token={} order={}", userId, token, exist.getId());
                return idemResult(exist);
            }
            if (!tryIdemLock(userId, token)) {
                // 并发同 token：可能另一请求刚提交完，再查一次；查不到就明确告诉用户别重复点
                Orders hit = findByIdemToken(userId, token);
                if (hit != null) return idemResult(hit);
                throw new BizException(ResultCode.REPEAT_SUBMIT, "订单正在提交中，请勿重复提交");
            }
        }
        try {
            return doCreate(userId, req, token);
        } catch (RuntimeException ex) {
            // 没落库就释放占位，否则用户改完地址重试会被自己的令牌挡住
            releaseIdemLock(userId, token);
            throw ex;
        }
    }

    /** 幂等令牌有效期：覆盖弱网重试的时间窗口（真正的正确性由唯一索引保证） */
    private static final long IDEM_TTL_SECONDS = 30 * 60;

    /** 规范化令牌：空串视为「未传」（退化为无幂等保护），超长直接拒绝以免撑爆 VARCHAR(64) */
    private String normalizeToken(String token) {
        if (token == null) return null;
        String t = token.trim();
        if (t.isEmpty()) return null;
        if (t.length() > 64) throw new BizException(ResultCode.PARAM_ERROR, "clientToken 长度超出限制");
        return t;
    }

    private Orders findByIdemToken(Long userId, String token) {
        if (token == null) return null;
        return ordersMapper.selectOne(new QueryWrapper<Orders>()
                .eq("user_id", userId).eq("client_token", token)
                .orderByDesc("id").last("limit 1"));
    }

    private boolean tryIdemLock(Long userId, String token) {
        if (token == null) return true;
        try {
            Boolean ok = redis.opsForValue().setIfAbsent("order:idem:" + userId + ":" + token,
                    "1", Duration.ofSeconds(IDEM_TTL_SECONDS));
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            // Redis 不可用不能连累下单：降级为「只靠 DB 唯一索引」兜底
            log.warn("idem lock unavailable, degrade to unique index: {}", e.getMessage());
            return true;
        }
    }

    private void releaseIdemLock(Long userId, String token) {
        if (token == null) return;
        try {
            redis.delete("order:idem:" + userId + ":" + token);
        } catch (Exception ignored) {
            // 释放失败无所谓：TTL 到期会自动清理
        }
    }

    /** 幂等命中：把已经落库的那一单原样返回，前端据此提示「订单已提交」 */
    private Map<String, Object> idemResult(Orders o) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", o.getId());
        r.put("orderId", o.getId());
        r.put("orderNo", o.getOrderNo());
        r.put("payAmount", o.getPayAmount());
        r.put("idempotent", true);
        return r;
    }

    /** 真正下单（库存已在调用前扣好，异常时由 doCreate 负责回补） */
    private Map<String, Object> doCreate(Long userId, WebDTO.OrderCreateReq req, String clientToken) {
        Merchant m = requireMerchant(req.getMerchantId());
        if (m.getOpenStatus() != 1) throw new BizException(ResultCode.SHOP_CLOSED, "店铺已打烊");

        List<Map<String, Object>> items = itemsOfCart(userId, req.getMerchantId());
        if (items.isEmpty()) throw new BizException("购物车为空");

        Address addr = addressMapper.selectById(req.getAddressId());
        if (addr == null || !addr.getUserId().equals(userId)) throw new BizException("收货地址无效");

        // ---- 配送范围校验：超出范围一律不能下单（库存都还没扣） ----
        Double distanceKm = distanceOf(m, addr);
        if (!pricingService.inRange(m.getDeliveryRadiusKm(), distanceKm)) {
            double radius = pricingService.radiusOf(m.getDeliveryRadiusKm());
            throw new BizException(ResultCode.OUT_OF_DELIVERY_RANGE,
                    "收货地址距商家约 " + String.format("%.1f", distanceKm) + "km，超出该商家的 "
                            + trim(radius) + "km 配送范围，请更换地址或选择其他商家");
        }

        // 加入购物车之后菜品可能被下架：下单前必须再确认一次，
        // 否则会出现「已下架的菜照样能下单」。
        for (Map<String, Object> item : items) {
            Dish d = dishMapper.selectById(((Number) item.get("dishId")).longValue());
            if (d == null || d.getStatus() != 1) {
                throw new BizException("「" + item.get("dishName") + "」已下架，请在购物车中移除后重新下单");
            }
        }

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

        try {
            return persistOrder(userId, req, m, addr, items, dishAmount, clientToken, distanceKm);
        } catch (RuntimeException ex) {
            // 关键：@Transactional 只回滚 MySQL，Redis 里已扣的库存不会自动回补。
            // 落库 / 发券 / 发 MQ 任何一步抛异常，这里手动把库存加回去，
            // 否则一次失败的下单会让库存永久少掉。
            for (Long d : deducted) restoreStock(d, qtyOf(items, d));
            throw ex;
        }
    }

    /** 扣完库存之后的落库流程（异常时由 doCreate() 负责回补 Redis 库存） */
    private Map<String, Object> persistOrder(Long userId, WebDTO.OrderCreateReq req, Merchant m,
                                             Address addr, List<Map<String, Object>> items,
                                             BigDecimal dishAmount, String clientToken, Double distanceKm) {
        // ---- 优惠券校验 ----
        BigDecimal discount = BigDecimal.ZERO;
        if (req.getUserCouponId() != null) {
            UserCouponMeta meta = loadCoupon(req.getUserCouponId(), userId, m.getId(), dishAmount);
            discount = meta.discount;
        }

        // ---- 距离计价 + 分账快照 ----
        BigDecimal deliveryFee = pricingService.deliveryFee(distanceKm == null ? 0 : distanceKm);
        BigDecimal packageFee = m.getPackageFee() == null ? BigDecimal.ZERO : m.getPackageFee();
        BigDecimal pay = dishAmount.add(deliveryFee).add(packageFee).subtract(discount).max(BigDecimal.valueOf(0.01));
        PricingService.IncomeSplit split = pricingService.split(dishAmount, packageFee, discount, deliveryFee);

        // ---- 落库 ----
        Orders order = new Orders();
        order.setOrderNo(orderNoUtil.next());
        order.setUserId(userId);
        order.setMerchantId(m.getId());
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("contact", addr.getContact());
        snap.put("phone", addr.getPhone());
        snap.put("detail", safeStr(addr.getProvince()) + safeStr(addr.getCity())
                + safeStr(addr.getDistrict()) + safeStr(addr.getDetail()));
        snap.put("lng", addr.getLng());
        snap.put("lat", addr.getLat());
        order.setAddressSnapshot(JsonUtil.toJson(snap));
        order.setDishAmount(dishAmount);
        order.setDeliveryFee(deliveryFee);
        order.setPackageFee(packageFee);
        order.setDiscountAmount(discount);
        order.setPayAmount(pay);
        order.setUserCouponId(req.getUserCouponId());
        order.setClientToken(clientToken);
        // 距离与分账：全部快照，事后改费率/改配送规则都不会篡改历史账
        order.setDistanceKm(distanceKm == null ? null : round(distanceKm, 3));
        order.setMerchantRate(split.merchantRate());
        order.setRiderRate(split.riderRate());
        order.setMerchantIncome(split.merchantIncome());
        order.setRiderIncome(split.riderIncome());
        order.setPlatformIncome(split.platformIncome());
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
        // MQ 抖动或未就绪时不能让「下单」整体失败：发不出去只告警，
        // 兜底由 ScheduleJobs#cancelTimeoutOrders 每分钟扫描一次完成（不依赖 MQ）。
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.DELAY_EXCHANGE,
                    RabbitMQConfig.DELAY_ROUTING_KEY, String.valueOf(order.getId()));
        } catch (Exception e) {
            log.error("[MQ] 订单 {} 延迟消息发送失败，改由定时任务兜底自动取消：{}", order.getId(), e.getMessage());
        }

        cartService.clearMerchant(userId, m.getId());
        return Map.of("id", order.getId(), "orderId", order.getId(),
                "orderNo", order.getOrderNo(), "payAmount", pay);
    }

    /** 地址拼接用：null 视为空串 */
    private static String safeStr(String s) {
        return s == null ? "" : s;
    }

    /** 取用户地址：指定 id 则校验归属；否则退回默认地址（没有则返回 null） */
    private Address resolveAddress(Long userId, Long addressId) {
        if (addressId != null) {
            Address a = addressMapper.selectById(addressId);
            if (a == null || !a.getUserId().equals(userId)) throw new BizException("收货地址无效");
            return a;
        }
        List<Address> list = addressMapper.selectList(new QueryWrapper<Address>()
                .eq("user_id", userId).orderByDesc("is_default").orderByDesc("id").last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 商家 → 收货地址的直线距离（km）。
     * 任一方缺坐标（用户只填了文字地址 / 商家没在地图上选点）返回 null，
     * 此时无法做范围判定，按「放行 + 只收起步费」处理。
     */
    private Double distanceOf(Merchant m, Address addr) {
        if (addr == null || m == null) return null;
        if (m.getLng() == null || m.getLat() == null || addr.getLng() == null || addr.getLat() == null) return null;
        if (m.getLng().doubleValue() == 0 || m.getLat().doubleValue() == 0) return null;
        if (addr.getLng().doubleValue() == 0 || addr.getLat().doubleValue() == 0) return null;
        return GeoUtil.distanceKm(m.getLng().doubleValue(), m.getLat().doubleValue(),
                addr.getLng().doubleValue(), addr.getLat().doubleValue());
    }

    private static BigDecimal round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }

    /** 去掉小数末尾多余的 0：5.00 → 5，1.50 → 1.5 */
    private static String trim(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
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

    /**
     * 能否查看 / 上报该订单的骑手位置。
     *
     * <p>WebSocket 的 SUBSCRIBE_ORDER 与 LOCATION_REPORT 都必须过这一关。
     * 原来这两条通道不做任何校验，等于「登录即可订阅任意订单看他人骑手坐标」，
     * 且「任意账号可往任意订单注入假坐标」。HTTP 的 POST /api/rider/location 同理。
     *
     * <p>口径与 {@link #detail} 一致：下单用户 / 该店商家 / 该单骑手 / 管理员。
     */
    public boolean canTrack(Long userId, Long orderId) {
        if (userId == null || orderId == null) return false;
        Orders o = ordersMapper.selectById(orderId);
        if (o == null) return false;
        User u = userMapper.selectById(userId);
        if (u == null) return false;
        if (Objects.equals(o.getUserId(), userId)) return true;
        if ("ADMIN".equals(u.getRole())) return true;
        if ("MERCHANT".equals(u.getRole())) {
            Merchant m = mOf(userId);
            return m != null && Objects.equals(m.getId(), o.getMerchantId());
        }
        if ("RIDER".equals(u.getRole())) {
            Rider r = riderOfUser(userId);
            return r != null && Objects.equals(o.getRiderId(), r.getId());
        }
        return false;
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
        // 快照可能是空（历史数据/异常写入），fromJson(null) 会直接 NPE
        vo.put("address", order.getAddressSnapshot() == null || order.getAddressSnapshot().isBlank()
                ? null : JsonUtil.fromJson(order.getAddressSnapshot(), Map.class));
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
        List<Orders> rows = p.getRecords();
        List<Map<String, Object>> records = new ArrayList<>();
        if (rows.isEmpty()) {
            return Map.of("records", records, "total", p.getTotal(), "pages", p.getPages());
        }

        // 批量取商家名，避免 N+1（订单列表页要显示店铺名）
        Map<Long, String> shopNames = new HashMap<>();
        for (Orders o : rows) shopNames.putIfAbsent(o.getMerchantId(), null);
        if (!shopNames.isEmpty()) {
            for (Merchant m : merchantMapper.selectBatchIds(shopNames.keySet())) {
                shopNames.put(m.getId(), m.getShopName());
            }
        }

        // 一次性把订单商品捞出来，顺便统计「共几件 / 首件是什么」，避免逐单查
        Set<Long> orderIds = new HashSet<>();
        for (Orders o : rows) orderIds.add(o.getId());
        Map<Long, OrderItem> firstItem = new HashMap<>();
        Map<Long, Integer> itemCount = new HashMap<>();
        for (OrderItem oi : orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().in("order_id", orderIds).orderByAsc("id"))) {
            firstItem.putIfAbsent(oi.getOrderId(), oi);
            itemCount.merge(oi.getOrderId(), 1, Integer::sum);
        }

        // 批量判断「是否已评价」：否则已完成的订单在列表里会一直显示「评价」按钮
        Set<Long> reviewed = new HashSet<>();
        for (Review r : reviewMapper.selectList(new QueryWrapper<Review>().in("order_id", orderIds))) {
            reviewed.add(r.getOrderId());
        }

        for (Orders o : rows) {
            Map<String, Object> vo = toVo(o);
            vo.put("merchantName", shopNames.get(o.getMerchantId()));
            vo.put("reviewed", reviewed.contains(o.getId()));
            vo.put("itemCount", itemCount.getOrDefault(o.getId(), 0));
            // 地址快照是 JSON，列表里给「拼好的可读文本」，别把 JSON 直接甩给用户
            Map<String, Object> addr = null;
            String snap = o.getAddressSnapshot();
            if (snap != null && !snap.isBlank()) {
                addr = JsonUtil.fromJson(snap, Map.class);
            }
            if (addr != null) {
                vo.put("address", addr);
                vo.put("addressText", addr.get("detail") == null ? "" : String.valueOf(addr.get("detail")));
            }
            vo.put("items", firstItem.get(o.getId()) == null
                    ? List.of() : List.of(firstItem.get(o.getId())));
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
        vo.put("packageFee", o.getPackageFee());
        vo.put("discountAmount", o.getDiscountAmount());
        vo.put("payAmount", o.getPayAmount());
        // 距离与分账：商家/骑手端看配送距离，管理端看收入构成
        vo.put("distanceKm", o.getDistanceKm());
        vo.put("merchantIncome", o.getMerchantIncome());
        vo.put("riderIncome", o.getRiderIncome());
        vo.put("platformIncome", o.getPlatformIncome());
        vo.put("status", o.getStatus());
        vo.put("remark", o.getRemark());
        vo.put("payTime", o.getPayTime());
        vo.put("acceptTime", o.getAcceptTime());
        vo.put("pickupTime", o.getPickupTime());
        vo.put("deliveredTime", o.getDeliveredTime());
        vo.put("cancelReason", o.getCancelReason());
        vo.put("createdAt", o.getCreatedAt());
        // 待支付订单的支付截止时间：支付页倒计时用。
        // 与 MQ 延迟队列的 TTL 共用同一个常量，避免两边对不上。
        if ("PENDING_PAYMENT".equals(o.getStatus()) && o.getCreatedAt() != null) {
            LocalDateTime deadline = o.getCreatedAt()
                    .plusMinutes(RabbitMQConfig.ORDER_PAY_TIMEOUT_MINUTES);
            vo.put("payDeadline", deadline);
            vo.put("payRemainSeconds",
                    Math.max(0, Duration.between(LocalDateTime.now(), deadline).getSeconds()));
        }
        return vo;
    }
}
