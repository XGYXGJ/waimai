package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.entity.Dish;
import com.waimai.entity.Merchant;
import com.waimai.entity.RecommendLog;
import com.waimai.entity.UserBehavior;
import com.waimai.mapper.DishMapper;
import com.waimai.mapper.MerchantMapper;
import com.waimai.mapper.RecommendLogMapper;
import com.waimai.mapper.UserBehaviorMapper;
import com.waimai.common.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐引擎：多路召回（HOT / ITEMCF / TAG / GEO）+ 融合精排 + 重排。
 * 权重从 sys_config 读取（默认 w_rec=0.5, w_quality=0.3, w_bid=0.2）。
 */
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final DishMapper dishMapper;
    private final MerchantMapper merchantMapper;
    private final RecommendLogMapper recommendLogMapper;
    private final UserBehaviorMapper userBehaviorMapper;
    private final StringRedisTemplate redis;
    private final SysConfigService sysConfigService;

    /**
     * 首页「AI每日推荐」：返回 6 个菜品候选（含基础分与召回来源）。
     * AI 理由由 AiService 回填，此处先返回候选 + 默认理由。
     */
    public List<Map<String, Object>> dailyRecommend(Long userId, Double userLng, Double userLat) {
        List<ScoredDish> candidates = recall(userId, userLng, userLat);
        List<ScoredDish> top = rank(candidates, null);
        List<ScoredDish> reranked = rerank(top, 6);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ScoredDish sd : reranked) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("dishId", sd.dish.getId());
            m.put("dishName", sd.dish.getName());
            m.put("image", sd.dish.getImage());
            m.put("price", sd.dish.getPrice());
            m.put("merchantId", sd.dish.getMerchantId());
            m.put("merchantName", sd.merchantName);
            m.put("source", sd.source);
            m.put("score", sd.finalScore);
            m.put("reason", "近期热门，很多人都在点"); // 默认理由，AiService 可覆盖
            result.add(m);

            // 曝光埋点（匿名访问不记录）
            if (userId != null) {
                RecommendLog log = new RecommendLog();
                log.setUserId(userId);
                log.setDishId(sd.dish.getId());
                log.setMerchantId(sd.dish.getMerchantId());
                log.setSource(sd.source);
                log.setScore(BigDecimal.valueOf(sd.finalScore));
                log.setIsExposed(1);
                log.setIsClicked(0);
                log.setIsOrdered(0);
                log.setReason("近期热门，很多人都在点");
                recommendLogMapper.insert(log);
            }
        }
        return result;
    }

    /* ---------- 多路召回 ---------- */

    private List<ScoredDish> recall(Long userId, Double lng, Double lat) {
        Map<Long, ScoredDish> pool = new LinkedHashMap<>();

        // 1. 热门召回 HOT：3km 内营业商家，按销量降序
        List<Merchant> openMerchants = merchantMapper.selectList(
                new QueryWrapper<Merchant>().eq("open_status", 1));
        Set<Long> nearIds = new HashSet<>();
        for (Merchant m : openMerchants) {
            if (m.getLng() == null || m.getLat() == null) continue;
            double dist = GeoUtil.distanceKm(
                    lng, lat,
                    m.getLng().doubleValue(), m.getLat().doubleValue());
            if (lng == null || dist <= 3.0) nearIds.add(m.getId());
        }
        if (!nearIds.isEmpty()) {
            List<Dish> hot = dishMapper.selectList(new QueryWrapper<Dish>()
                    .eq("status", 1).in("merchant_id", nearIds)
                    .orderByDesc("monthly_sales").last("limit 20"));
            for (Dish d : hot) putPool(pool, d, "HOT", 1.0);
        }

        // 2. 协同过滤 ITEMCF：近90天下单菜品的相似菜品
        if (userId != null) {
            List<UserBehavior> orders = userBehaviorMapper.selectList(new QueryWrapper<UserBehavior>()
                    .eq("user_id", userId).eq("action", "ORDER")
                    .ge("created_at", LocalDateTime.now().minusDays(90)));
            Set<Long> seedDishes = orders.stream().map(UserBehavior::getDishId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            for (Long seed : seedDishes) {
                List<String> sim = redis.opsForList().range("dish:sim:" + seed, 0, 19);
                if (sim == null) continue;
                for (String s : sim) {
                    try {
                        Long dishId = Long.valueOf(s.split(":")[0]);
                        double score = Double.parseDouble(s.split(":")[1]);
                        Dish d = dishMapper.selectById(dishId);
                        if (d != null && d.getStatus() == 1) putPool(pool, d, "ITEMCF", score);
                    } catch (Exception ignore) { }
                }
            }
        }

        // 3. 标签召回 TAG：画像 Top 标签
        if (userId != null) {
            Map<Object, Object> profile = redis.opsForHash().entries("user:profile:" + userId);
            if (profile != null && !profile.isEmpty()) {
                profile.entrySet().stream()
                        .sorted((a, b) -> Double.compare(
                                Double.parseDouble(String.valueOf(b.getValue())),
                                Double.parseDouble(String.valueOf(a.getValue()))))
                        .limit(5)
                        .forEach(e -> {
                            String tag = String.valueOf(e.getKey());
                            List<Dish> tagged = dishMapper.selectList(new QueryWrapper<Dish>()
                                    .eq("status", 1).like("tags", tag).last("limit 10"));
                            for (Dish d : tagged) putPool(pool, d, "TAG", 1.0);
                        });
            }
        }

        // 4. 地理召回 GEO：最近商家的高销量菜
        if (!nearIds.isEmpty()) {
            List<Dish> geo = dishMapper.selectList(new QueryWrapper<Dish>()
                    .eq("status", 1).in("merchant_id", nearIds)
                    .orderByDesc("monthly_sales").last("limit 20"));
            for (Dish d : geo) putPool(pool, d, "GEO", 1.0);
        }

        return new ArrayList<>(pool.values());
    }

    private void putPool(Map<Long, ScoredDish> pool, Dish d, String source, double score) {
        ScoredDish sd = pool.get(d.getId());
        if (sd == null) {
            sd = new ScoredDish(d, source, score);
            Merchant m = merchantMapper.selectById(d.getMerchantId());
            sd.merchantName = m == null ? "" : m.getShopName();
            sd.merchantRating = (m == null || m.getRating() == null) ? 4.5 : m.getRating().doubleValue();
            pool.put(d.getId(), sd);
        } else {
            // 多路命中：取来源权重更高的一路（记录主要来源）
            if (sourceWeight(source) > sourceWeight(sd.source)) sd.source = source;
            sd.recScore = Math.max(sd.recScore, score);
        }
    }

    private static double sourceWeight(String source) {
        return switch (source) {
            case "ITEMCF" -> 0.4;
            case "TAG" -> 0.3;
            case "HOT" -> 0.15;
            case "GEO" -> 0.15;
            default -> 0.1;
        };
    }

    /* ---------- 融合精排 ---------- */

    private List<ScoredDish> rank(List<ScoredDish> candidates, String keyword) {
        double wRec = sysConfigService.getDouble("recommend.weight.rec", 0.5);
        double wQuality = sysConfigService.getDouble("recommend.weight.quality", 0.3);
        double wBid = sysConfigService.getDouble("recommend.weight.bid", 0.2);

        // 归一化 base
        double maxSales = candidates.stream().mapToDouble(s -> s.dish.getMonthlySales()).max().orElse(1);
        double maxRating = candidates.stream().mapToDouble(s -> s.merchantRating).max().orElse(5);

        for (ScoredDish s : candidates) {
            double recScore = s.recScore * sourceWeight(s.source);
            double qualityScore = 0.4 * (s.dish.getMonthlySales() / maxSales)
                    + 0.4 * (s.merchantRating / 5.0)
                    + 0.2 * (s.dish.getMonthlySales() / maxSales); // 简化：销量再加权
            double bidScore = 0; // 自然排序场景；竞价场景由 MerchantService 覆盖
            double epsilon = Math.random() * 0.01;
            s.finalScore = wRec * recScore + wQuality * qualityScore + wBid * bidScore + epsilon;
        }
        candidates.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));
        return candidates;
    }

    /* ---------- 重排 ---------- */

    private List<ScoredDish> rerank(List<ScoredDish> ranked, int n) {
        List<ScoredDish> out = new ArrayList<>();
        Map<Long, Integer> merchantCount = new HashMap<>();
        for (ScoredDish s : ranked) {
            int cnt = merchantCount.getOrDefault(s.dish.getMerchantId(), 0);
            if (cnt >= 2) continue; // 同一商家最多2个
            out.add(s);
            merchantCount.merge(s.dish.getMerchantId(), 1, Integer::sum);
            if (out.size() >= n) break;
        }
        return out;
    }

    /** 推荐点击回传（实验指标） */
    public void feedback(Long userId, Long logId) {
        RecommendLog log = recommendLogMapper.selectById(logId);
        if (log == null || !log.getUserId().equals(userId)) return;
        log.setIsClicked(1);
        recommendLogMapper.updateById(log);
    }

    static class ScoredDish {
        Dish dish;
        String source;
        String merchantName = "";
        double recScore;
        double merchantRating = 4.5;
        double finalScore;

        ScoredDish(Dish d, String source, double recScore) {
            this.dish = d;
            this.source = source;
            this.recScore = recScore;
        }
    }
}
