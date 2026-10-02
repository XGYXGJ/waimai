package com.waimai.websocket;

import com.waimai.common.util.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * WebSocket 握手鉴权：/ws?token=xxx
 * 校验通过后把 userId / role 放入 attributes。
 */
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    public WsHandshakeInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request.getURI());
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String[] parsed = jwtUtil.parse(token);
        if (parsed == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put("userId", Long.valueOf(parsed[0]));
        attributes.put("role", parsed[1]);
        return true;
    }

    private String extractToken(URI uri) {
        String query = uri.getQuery();
        if (query == null) return null;
        for (String kv : query.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && "token".equals(kv.substring(0, i))) {
                return URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
