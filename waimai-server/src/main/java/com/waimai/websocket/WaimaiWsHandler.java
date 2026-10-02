package com.waimai.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waimai.service.TrackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 消息协议：
 * 客户端→服务端: PING / SUBSCRIBE_ORDER / UNSUBSCRIBE_ORDER / LOCATION_REPORT(骑手)
 * 服务端→客户端: PONG / ORDER_STATUS / NEW_ORDER / RIDER_LOCATION / NOTIFICATION / ERROR
 */
@Slf4j
@Component
public class WaimaiWsHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TrackService trackService;

    public WaimaiWsHandler(TrackService trackService) {
        this.trackService = trackService;
    }

    // userId -> session（一个用户一处登录，重复登录顶号）
    public static final Map<Long, WebSocketSession> USER_SESSIONS = new ConcurrentHashMap<>();
    // orderId -> 订阅该订单骑手位置的用户集合
    public static final Map<Long, java.util.Set<Long>> ORDER_SUBSCRIBERS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        WebSocketSession old = USER_SESSIONS.put(userId, session);
        if (old != null && old.isOpen()) {
            try { old.close(CloseStatus.POLICY_VIOLATION); } catch (Exception ignored) { }
        }
        log.info("ws connected: userId={}", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        JsonNode node;
        try {
            node = mapper.readTree(message.getPayload());
        } catch (Exception e) {
            send(session, Map.of("type", "ERROR", "msg", "消息格式错误"));
            return;
        }
        String type = node.path("type").asText("");
        switch (type) {
            case "PING" -> send(session, Map.of("type", "PONG"));
            case "SUBSCRIBE_ORDER" -> {
                long orderId = node.path("orderId").asLong();
                ORDER_SUBSCRIBERS.computeIfAbsent(orderId, k -> ConcurrentHashMap.newKeySet()).add(userId);
            }
            case "UNSUBSCRIBE_ORDER" -> {
                long orderId = node.path("orderId").asLong();
                java.util.Set<Long> set = ORDER_SUBSCRIBERS.get(orderId);
                if (set != null) set.remove(userId);
            }
            case "LOCATION_REPORT" -> {
                // 骑手位置上报（真实 GPS 与模拟骑行共用）
                double lng = node.path("lng").asDouble();
                double lat = node.path("lat").asDouble();
                Long orderId = node.path("orderId").asLong(0);
                trackService.report(userId, lng, lat, orderId == 0 ? null : orderId);
            }
            default -> send(session, Map.of("type", "ERROR", "msg", "未知消息类型: " + type));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null && USER_SESSIONS.get(userId) == session) {
            USER_SESSIONS.remove(userId);
            ORDER_SUBSCRIBERS.values().forEach(s -> s.remove(userId));
        }
        log.info("ws closed: userId={}, status={}", userId, status);
    }

    private void send(WebSocketSession session, Object payload) {
        try {
            if (session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
        } catch (Exception e) {
            log.warn("ws send fail: {}", e.getMessage());
        }
    }
}
