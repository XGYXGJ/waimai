package com.waimai.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WS 推送工具：供各业务服务调用。
 */
@Slf4j
@Component
public class WsPusher {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 推送给指定用户（不在线则忽略，站内信兜底） */
    public void pushToUser(Long userId, Map<String, Object> payload) {
        WebSocketSession session = WaimaiWsHandler.USER_SESSIONS.get(userId);
        if (session == null || !session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
        } catch (Exception e) {
            log.warn("push to {} fail: {}", userId, e.getMessage());
        }
    }

    /** 推送订单状态变化给相关方 */
    public void pushOrderStatus(Long userId, Long orderId, String orderNo, String from, String to) {
        pushToUser(userId, Map.of(
                "type", "ORDER_STATUS",
                "orderId", orderId,
                "orderNo", orderNo,
                "from", from == null ? "" : from,
                "to", to,
                "ts", System.currentTimeMillis()));
    }

    /** 新订单提醒（商户） */
    public void pushNewOrder(Long merchantUserId, Long orderId, double payAmount) {
        pushToUser(merchantUserId, Map.of(
                "type", "NEW_ORDER",
                "orderId", orderId,
                "payAmount", payAmount,
                "ts", System.currentTimeMillis()));
    }

    /** 骑手位置（推给订阅该订单的用户 + 该订单所属商家，两端都要看到配送进度） */
    public void pushRiderLocation(Long orderId, double lng, double lat) {
        var subscribers = WaimaiWsHandler.ORDER_SUBSCRIBERS.get(orderId);
        String json;
        try {
            json = mapper.writeValueAsString(Map.of(
                    "type", "RIDER_LOCATION",
                    "orderId", orderId,
                    "lng", lng,
                    "lat", lat,
                    "ts", System.currentTimeMillis()));
        } catch (Exception e) {
            return;
        }
        if (subscribers != null) {
            sendToUsers(subscribers, json);
        }
        for (Long userId : extraWatchers.getOrDefault(orderId, java.util.Set.of())) {
            sendToUser(userId, json);
        }
    }

    /**
     * 登记「除订阅者之外也要看这个订单骑手位置」的人（目前是商家）。
     * 接单时登记一次即可。
     *
     * <p>注意：必须配套清理 —— 订单 id 单调递增，只登记不清理就是无上限泄漏。
     * 断开连接时由 {@link #unwatchAll(Long)} 清该用户全部登记，
     * 订单进入终态时由 {@link #unwatchOrder(Long)} 清整个订单。
     */
    public void watchRiderLocation(Long orderId, Long userId) {
        if (orderId == null || userId == null) return;
        extraWatchers.computeIfAbsent(orderId, k -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    /** 断开连接时移除该用户登记过的全部订单观察 */
    public void unwatchAll(Long userId) {
        if (userId == null) return;
        extraWatchers.values().forEach(set -> set.remove(userId));
        extraWatchers.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /** 订单结束（送达/取消/退款）后释放该订单的观察登记 */
    public void unwatchOrder(Long orderId) {
        if (orderId == null) return;
        extraWatchers.remove(orderId);
    }

    /** orderId -> 需要额外接收骑手位置的用户（商家） */
    private final Map<Long, java.util.Set<Long>> extraWatchers = new ConcurrentHashMap<>();

    private void sendToUsers(java.util.Collection<Long> userIds, String json) {
        for (Long userId : userIds) {
            sendToUser(userId, json);
        }
    }

    private void sendToUser(Long userId, String json) {
        WebSocketSession session = WaimaiWsHandler.USER_SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(json));
            } catch (Exception ignored) { }
        }
    }

    /**
     * 会话消息广播：三方（用户 / 商家 / 骑手）都收。
     * 三端原本各自 3 秒轮询，加 WS 后消息能秒达；轮询作为兜底保留。
     */
    public void pushImMessage(Long sessionId, Map<String, Object> message, java.util.Collection<Long> receivers) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("type", "IM_MESSAGE");
        payload.put("sessionId", sessionId);
        payload.put("message", message);
        payload.put("ts", System.currentTimeMillis());
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            return;
        }
        sendToUsers(receivers, json);
    }

    /** 通知 */
    public void pushNotification(Long userId, String title, String content) {
        pushToUser(userId, Map.of(
                "type", "NOTIFICATION",
                "title", title,
                "content", content == null ? "" : content,
                "ts", System.currentTimeMillis()));
    }
}
