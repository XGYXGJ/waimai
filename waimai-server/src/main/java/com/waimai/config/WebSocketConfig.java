package com.waimai.config;

import com.waimai.websocket.WaimaiWsHandler;
import com.waimai.websocket.WsHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WaimaiWsHandler waimaiWsHandler;
    private final WsHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(WaimaiWsHandler waimaiWsHandler, WsHandshakeInterceptor handshakeInterceptor) {
        this.waimaiWsHandler = waimaiWsHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(waimaiWsHandler, "/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
