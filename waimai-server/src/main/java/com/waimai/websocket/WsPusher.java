package com.waimai.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

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

    /** 骑手位置（推给订阅该订单的用户） */
    public void pushRiderLocation(Long orderId, double lng, double lat) {
        var subscribers = WaimaiWsHandler.ORDER_SUBSCRIBERS.get(orderId);
        if (subscribers == null || subscribers.isEmpty()) return;
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
        for (Long userId : subscribers) {
            WebSocketSession session = WaimaiWsHandler.USER_SESSIONS.get(userId);
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (Exception ignored) { }
            }
        }
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
