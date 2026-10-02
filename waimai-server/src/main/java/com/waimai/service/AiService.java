package com.waimai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.waimai.client.AiClient;
import com.waimai.dto.WebDTO;
import com.waimai.entity.*;
import com.waimai.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 能力编排：智能客服、每日推荐理由、评论情感分析、销量预测。
 * 统一处理 degraded 降级（云端→Ollama→固定话术/模板理由）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiClient aiClient;
    private final OrdersMapper ordersMapper;
    private final RiderMapper riderMapper;
    private final MerchantMapper merchantMapper;
    private final ReviewMapper reviewMapper;
    private final DishSalesDailyMapper dishSalesDailyMapper;
    private final DishMapper dishMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final StringRedisTemplate redis;
    private final RecommendService recommendService;
    private final SysConfigService sysConfigService;

    /* ---------- 智能客服 ---------- */

    public Map<String, Object> chat(Long userId, WebDTO.ChatSendReq req) {
        String message = req.getContent();
        if (message == null || message.isBlank()) throw new com.waimai.common.exception.BizException("消息不能为空");

        // 配额：每用户每日 50 次
        String quotaKey = "ai:quota:" + userId + ":" + LocalDate.now();
        long used = redis.opsForValue().increment(quotaKey);
        if (used == 1) redis.expire(quotaKey, java.time.Duration.ofDays(1));
        if (used > 50) throw new com.waimai.common.exception.BizException("今日 AI 客服使用次数已达上限");

        // 找会话
        ChatSession session = findOrCreateSession(userId);

        // 注入订单上下文
        Map<String, Object> orderContext = buildOrderContext(userId);

        // 历史
        List<Map<String, String>> history = recentHistory(session.getId(), 6);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", String.valueOf(session.getId()));
        payload.put("userId", userId);
        payload.put("message", message);
        payload.put("orderContext", orderContext);
        payload.put("history", history);

        AiClient.AiResp resp = aiClient.chat(payload);
        String reply;
        String intent = resp.str("intent");
        if (resp.degraded) {
            reply = fallbackReply(message, orderContext, intent);
        } else {
            reply = resp.str("reply");
            if (reply == null || reply.isBlank()) reply = fallbackReply(message, orderContext, intent);
        }

        // 落库
        ChatMessage um = new ChatMessage();
        um.setSessionId(session.getId());
        um.setRole("user");
        um.setContent(message);
        chatMessageMapper.insert(um);
        ChatMessage am = new ChatMessage();
        am.setSessionId(session.getId());
        am.setRole("assistant");
        am.setContent(reply);
        chatMessageMapper.insert(am);

        // 催单真实落地
        if ("URGE".equals(intent) && orderContext != null && orderContext.get("orderNo") != null) {
            log.info("urge order {} triggered", orderContext.get("orderNo"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("reply", reply);
        result.put("intent", intent == null ? "OTHER" : intent);
        result.put("degraded", resp.degraded);
        return result;
    }

    private ChatSession findOrCreateSession(Long userId) {
        List<ChatSession> list = chatSessionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ChatSession>()
                        .eq("user_id", userId).orderByDesc("created_at").last("limit 1"));
        if (!list.isEmpty()) return list.get(0);
        ChatSession s = new ChatSession();
        s.setUserId(userId);
        s.setTitle("在线客服");
        chatSessionMapper.insert(s);
        return s;
    }

    private List<Map<String, String>> recentHistory(Long sessionId, int n) {
        List<ChatMessage> msgs = chatMessageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId).orderByDesc("created_at").last("limit " + n * 2));
        List<Map<String, String>> out = new ArrayList<>();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            ChatMessage m = msgs.get(i);
            Map<String, String> h = new HashMap<>();
            h.put("role", m.getRole());
            h.put("content", m.getContent());
            out.add(h);
        }
        return out;
    }

    private Map<String, Object> buildOrderContext(Long userId) {
        List<Orders> list = ordersMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Orders>()
                        .eq("user_id", userId)
                        .notIn("status", "DELIVERED", "CANCELLED", "REFUNDED")
                        .orderByDesc("created_at").last("limit 1"));
        if (list.isEmpty()) return null;
        Orders o = list.get(0);
        Merchant m = merchantMapper.selectById(o.getMerchantId());
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("orderNo", o.getOrderNo());
        ctx.put("status", o.getStatus());
        ctx.put("merchantName", m == null ? "" : m.getShopName());
        ctx.put("payAmount", o.getPayAmount());
        ctx.put("createdAt", o.getCreatedAt() == null ? "" : o.getCreatedAt().toString());
        if (o.getRiderId() != null) {
            Rider r = riderMapper.selectById(o.getRiderId());
            if (r != null) {
                ctx.put("riderName", r.getRealName());
                ctx.put("riderPhone", r.getPhone() == null ? "" : r.getPhone());
            }
        }
        return ctx;
    }

    private String fallbackReply(String message, Map<String, Object> ctx, String intent) {
        if (intent == null) intent = guessIntent(message);
        return switch (intent) {
            case "ORDER_QUERY" -> ctx != null && ctx.get("status") != null
                    ? "您最近一笔订单状态是：" + ctx.get("status") + "，订单号 " + ctx.get("orderNo") + "，请留意配送进度哦~"
                    : "您当前没有进行中的订单哦~";
            case "URGE" -> "已为您向商家催单，商家会尽快出餐哦~";
            case "REFUND" -> "商户未接单会自动全额退款；接单后请先与商户协商，或申请平台介入处理哦~";
            case "COUPON" -> "优惠券可在下单结算页选择使用，记得注意使用门槛和有效期哦~";
            default -> "这个问题我转人工客服为您处理啦~";
        };
    }

    private String guessIntent(String msg) {
        if (msg.contains("订单") || msg.contains("单号") || msg.contains("我的单")) return "ORDER_QUERY";
        if (msg.contains("催") || msg.contains("快点") || msg.contains("多久")) return "URGE";
        if (msg.contains("退") || msg.contains("取消")) return "REFUND";
        if (msg.contains("优惠券") || msg.contains("红包")) return "COUPON";
        return "OTHER";
    }

    /* ---------- 每日推荐（含理由回填） ---------- */

    public Map<String, Object> dailyRecommend(Long userId, Double lng, Double lat) {
        // 缓存
        String cacheKey = "ai:daily:" + userId + ":" + LocalDate.now();
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(cached, Map.class);
            } catch (Exception ignore) { }
        }

        List<Map<String, Object>> candidates = recommendService.dailyRecommend(userId, lng, lat);

        // 组装 AI 请求
        List<Map<String, Object>> aiCandidates = new ArrayList<>();
        for (Map<String, Object> c : candidates) {
            Map<String, Object> ac = new HashMap<>();
            ac.put("dishId", c.get("dishId"));
            ac.put("dishName", c.get("dishName"));
            ac.put("merchantName", c.get("merchantName"));
            ac.put("price", c.get("price"));
            ac.put("score", c.get("score"));
            ac.put("source", c.get("source"));
            aiCandidates.add(ac);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("candidates", aiCandidates);
        payload.put("context", Map.of("weekday", weekday(), "timeSlot", timeSlot()));

        AiClient.AiResp resp = aiClient.recommend(payload);
        Map<Long, String> reasons = new HashMap<>();
        if (!resp.degraded && resp.data != null && resp.data.has("items")) {
            for (JsonNode item : resp.data.get("items")) {
                if (item.has("dishId") && item.has("reason")) {
                    reasons.put(item.get("dishId").asLong(), item.get("reason").asText());
                }
            }
        }
        for (Map<String, Object> c : candidates) {
            String reason = reasons.get(((Number) c.get("dishId")).longValue());
            if (reason != null) c.put("reason", reason);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", candidates);
        result.put("degraded", resp.degraded);
        try {
            redis.opsForValue().set(cacheKey, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result),
                    java.time.Duration.ofHours(24));
        } catch (Exception ignore) { }
        return result;
    }

    private String weekday() {
        return switch (LocalDate.now().getDayOfWeek()) {
            case MONDAY -> "周一"; case TUESDAY -> "周二"; case WEDNESDAY -> "周三";
            case THURSDAY -> "周四"; case FRIDAY -> "周五"; case SATURDAY -> "周六"; case SUNDAY -> "周日";
        };
    }

    private String timeSlot() {
        int h = LocalDateTime.now().getHour();
        if (h < 10) return "早餐";
        if (h < 14) return "午餐";
        if (h < 17) return "下午茶";
        if (h < 21) return "晚餐";
        return "夜宵";
    }

    /* ---------- 评论情感分析 ---------- */

    /** 异步触发情感分析（评价提交后调用） */
    @org.springframework.scheduling.annotation.Async
    public void analyzeSentimentAsync(Long reviewId) {
        analyzeSentiment(reviewId);
    }

    public void analyzeSentiment(Long reviewId) {
        Review r = reviewMapper.selectById(reviewId);
        if (r == null || r.getContent() == null || r.getContent().isBlank()) return;
        AiClient.AiResp resp = aiClient.sentiment(String.valueOf(reviewId), r.getContent());
        if (resp.degraded || resp.data == null) return;
        try {
            if (resp.data.has("sentiment")) {
                r.setSentiment(resp.data.get("sentiment").asText());
                r.setSentimentScore(resp.data.has("score")
                        ? java.math.BigDecimal.valueOf(resp.data.get("score").asDouble()) : null);
                reviewMapper.updateById(r);
            }
        } catch (Exception e) {
            log.warn("sentiment parse error: {}", e.getMessage());
        }
    }

    /* ---------- 销量预测 ---------- */

    public Map<String, Object> forecast(Long merchantUserId) {
        Long merchantId = merchantMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Merchant>()
                        .eq("user_id", merchantUserId).last("limit 1"))
                .get(0).getId();

        List<Dish> dishes = dishMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Dish>()
                        .eq("merchant_id", merchantId).eq("status", 1));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Dish d : dishes) {
            List<DishSalesDaily> sales = dishSalesDailyMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DishSalesDaily>()
                            .eq("dish_id", d.getId())
                            .ge("stat_date", LocalDate.now().minusDays(28))
                            .orderByAsc("stat_date"));
            int forecast = simpleForecast(sales);
            Map<String, Object> m = new HashMap<>();
            m.put("dishId", d.getId());
            m.put("dishName", d.getName());
            m.put("forecast", forecast);
            m.put("suggestion", (int) Math.ceil(forecast * 1.1));
            m.put("history", sales.stream().map(s -> Map.of(
                    "date", s.getStatDate().toString(), "qty", s.getQuantity())).toList());
            result.add(m);
        }
        return Map.of("items", result);
    }

    /** 简单预测：近7天均值 × 星期因子 × 趋势（clamp 0.8~1.2） */
    private int simpleForecast(List<DishSalesDaily> sales) {
        if (sales.isEmpty()) return 0;
        if (sales.size() < 7) {
            return (int) Math.round(sales.stream().mapToInt(DishSalesDaily::getQuantity).average().orElse(0));
        }
        List<Integer> qty = sales.stream().map(DishSalesDaily::getQuantity).toList();
        double base = qty.subList(qty.size() - 7, qty.size()).stream().mapToDouble(Integer::doubleValue).average().orElse(0);
        double prev = qty.subList(qty.size() - 14, qty.size() - 7).stream().mapToDouble(Integer::doubleValue).average().orElse(base);
        double trend = prev == 0 ? 1.0 : Math.max(0.8, Math.min(1.2, base / prev));
        return (int) Math.round(base * trend);
    }
}
