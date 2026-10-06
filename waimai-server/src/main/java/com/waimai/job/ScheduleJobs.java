package com.waimai.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.config.RabbitMQConfig;
import com.waimai.entity.*;
import com.waimai.mapper.*;
import com.waimai.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 定时任务：ItemCF 相似度、用户画像、销量日聚合、竞价日预算重置、优惠券过期、
 * 超时未支付订单兜底取消（MQ 不可用时的最后防线）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleJobs {

    private final UserBehaviorMapper userBehaviorMapper;
    private final DishMapper dishMapper;
    private final DishSalesDailyMapper dishSalesDailyMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrdersMapper ordersMapper;
    private final BidCampaignMapper bidCampaignMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponMapper couponMapper;
    private final StringRedisTemplate redis;
    private final OrderService orderService;

    /* ---------- ItemCF 相似度（每日 3:00） ---------- */

    @Scheduled(cron = "0 0 3 * * ?")
    public void computeItemSimilarity() {
        log.info("[job] ItemCF similarity start");
        LocalDateTime since = LocalDateTime.now().minusDays(90);
        List<UserBehavior> behaviors = userBehaviorMapper.selectList(
                new QueryWrapper<UserBehavior>().ge("created_at", since));

        // 用户 -> 当天交互的菜品集合（简化：按用户聚合，不区分天）
        Map<Long, Set<Long>> userDishes = new HashMap<>();
        for (UserBehavior b : behaviors) {
            if (b.getDishId() == null) continue;
            userDishes.computeIfAbsent(b.getUserId(), k -> new HashSet<>()).add(b.getDishId());
        }

        // 共现计数
        Map<Long, Map<Long, Integer>> cooccur = new HashMap<>();
        Map<Long, Integer> dishPop = new HashMap<>();
        for (Set<Long> dishes : userDishes.values()) {
            List<Long> list = new ArrayList<>(dishes);
            for (Long d : list) dishPop.merge(d, 1, Integer::sum);
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    Long a = list.get(i), b = list.get(j);
                    cooccur.computeIfAbsent(a, k -> new HashMap<>()).merge(b, 1, Integer::sum);
                    cooccur.computeIfAbsent(b, k -> new HashMap<>()).merge(a, 1, Integer::sum);
                }
            }
        }

        // 写 Redis dish:sim:{dishId} = ["dishId:score", ...]（Top 50）
        for (Map.Entry<Long, Map<Long, Integer>> e : cooccur.entrySet()) {
            Long a = e.getKey();
            double popA = Math.max(dishPop.getOrDefault(a, 1), 1);
            List<Map.Entry<Long, Integer>> sorted = e.getValue().entrySet().stream()
                    .sorted((x, y) -> y.getValue() - x.getValue())
                    .limit(50)
                    .toList();
            List<String> sims = sorted.stream()
                    .map(x -> x.getKey() + ":" + (x.getValue() / Math.sqrt(popA * Math.max(dishPop.getOrDefault(x.getKey(), 1), 1))))
                    .toList();
            String key = "dish:sim:" + a;
            redis.delete(key);
            if (!sims.isEmpty()) redis.opsForList().rightPushAll(key, sims);
        }
        log.info("[job] ItemCF similarity done, dishes={}", cooccur.size());
    }

    /* ---------- 用户画像（每日 3:30） ---------- */

    @Scheduled(cron = "0 30 3 * * ?")
    public void updateUserProfile() {
        log.info("[job] user profile update start");
        LocalDateTime since = LocalDateTime.now().minusDays(90);
        List<UserBehavior> behaviors = userBehaviorMapper.selectList(
                new QueryWrapper<UserBehavior>().ge("created_at", since));

        Map<Long, Map<String, Double>> profile = new HashMap<>();
        for (UserBehavior b : behaviors) {
            if (b.getDishId() == null) continue;
            Dish d = dishMapper.selectById(b.getDishId());
            if (d == null || d.getTags() == null) continue;
            double weight = switch (b.getAction()) {
                case "VIEW" -> 1;
                case "CART", "FAV" -> 2;
                case "ORDER" -> 3;
                default -> 0;
            };
            for (String tag : d.getTags().split("[,，]")) {
                tag = tag.trim();
                if (tag.isEmpty()) continue;
                profile.computeIfAbsent(b.getUserId(), k -> new HashMap<>())
                        .merge(tag, weight, Double::sum);
            }
        }
        for (Map.Entry<Long, Map<String, Double>> e : profile.entrySet()) {
            String key = "user:profile:" + e.getKey();
            redis.delete(key);
            Map<String, String> hash = e.getValue().entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(10)
                    .collect(Collectors.toMap(Map.Entry::getKey, x -> String.valueOf(x.getValue()),
                            (a, b) -> a, LinkedHashMap::new));
            if (!hash.isEmpty()) redis.opsForHash().putAll(key, hash);
        }
        log.info("[job] user profile update done, users={}", profile.size());
    }

    /* ---------- 销量日聚合（每日 1:00） ---------- */

    @Scheduled(cron = "0 0 1 * * ?")
    public void aggregateDailySales() {
        log.info("[job] daily sales aggregate start");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        // 昨日完成的订单
        List<Orders> orders = ordersMapper.selectList(new QueryWrapper<Orders>()
                .eq("status", "DELIVERED")
                .ge("updated_at", yesterday.atStartOfDay())
                .lt("updated_at", LocalDate.now().atStartOfDay()));
        Map<Long, int[]> agg = new HashMap<>(); // dishId -> [qty, amount(分)]
        for (Orders o : orders) {
            for (OrderItem oi : orderItemMapper.selectList(
                    new QueryWrapper<OrderItem>().eq("order_id", o.getId()))) {
                int[] v = agg.computeIfAbsent(oi.getDishId(), k -> new int[2]);
                v[0] += oi.getQuantity();
                v[1] += oi.getPrice().multiply(java.math.BigDecimal.valueOf(oi.getQuantity()))
                        .multiply(java.math.BigDecimal.valueOf(100)).intValue();
            }
        }
        for (Map.Entry<Long, int[]> e : agg.entrySet()) {
            Dish d = dishMapper.selectById(e.getKey());
            if (d == null) continue;
            DishSalesDaily s = new DishSalesDaily();
            s.setMerchantId(d.getMerchantId());
            s.setDishId(e.getKey());
            s.setStatDate(yesterday);
            s.setQuantity(e.getValue()[0]);
            s.setAmount(java.math.BigDecimal.valueOf(e.getValue()[1]).divide(java.math.BigDecimal.valueOf(100)));
            dishSalesDailyMapper.insert(s);
        }
        log.info("[job] daily sales aggregate done, dishes={}", agg.size());
    }

    /* ---------- 竞价日预算重置（每日 0:00） ---------- */

    @Scheduled(cron = "0 0 0 * * ?")
    public void resetBidBudget() {
        List<BidCampaign> list = bidCampaignMapper.selectList(
                new QueryWrapper<BidCampaign>().eq("status", 1));
        for (BidCampaign c : list) {
            c.setTodaySpent(java.math.BigDecimal.ZERO);
            bidCampaignMapper.updateById(c);
        }
        log.info("[job] bid budget reset done, campaigns={}", list.size());
    }

    /* ---------- 优惠券过期（每日 4:00） ---------- */

    @Scheduled(cron = "0 0 4 * * ?")
    public void expireCoupons() {
        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> list = userCouponMapper.selectList(
                new QueryWrapper<UserCoupon>().eq("status", 0));
        int cnt = 0;
        for (UserCoupon uc : list) {
            Coupon c = couponMapper.selectById(uc.getCouponId());
            if (c == null || c.getEndTime() == null) continue;
            if (c.getEndTime().isBefore(now)) {
                uc.setStatus(2);
                userCouponMapper.updateById(uc);
                cnt++;
            }
        }
        log.info("[job] expire coupons done, expired={}", cnt);
    }

    /* ---------- 超时未支付订单兜底取消（每分钟） ---------- */

    /**
     * MQ 正常时由 {@link com.waimai.mq.OrderTimeoutListener} 消费延迟消息取消订单；
     * RabbitMQ 不可用（或消息丢失）时，这个任务就是最后防线：每分钟扫一遍
     * 「状态仍是待支付、且下单已超过 {@link RabbitMQConfig#ORDER_PAY_TIMEOUT_MINUTES} 分钟」的订单。
     * 取消逻辑复用 OrderService#cancelOnTimeout（本身对状态做了幂等判断，重复触发无副作用）。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void cancelTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(RabbitMQConfig.ORDER_PAY_TIMEOUT_MINUTES);
        List<Orders> list = ordersMapper.selectList(new QueryWrapper<Orders>()
                .eq("status", "PENDING_PAYMENT")
                .lt("created_at", deadline));
        if (list.isEmpty()) {
            return;
        }
        int done = 0;
        for (Orders order : list) {
            try {
                orderService.cancelOnTimeout(order.getId());
                done++;
            } catch (Exception e) {
                log.warn("[job] 兜底取消订单 {} 失败：{}", order.getId(), e.getMessage());
            }
        }
        log.info("[job] timeout orders cancelled, scanned={}, cancelled={}", list.size(), done);
    }
}
