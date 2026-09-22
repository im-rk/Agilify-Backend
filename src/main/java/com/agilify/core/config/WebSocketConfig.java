package com.agilify.core.config;

import com.agilify.core.websocket.CrdtDocWebSocketHandler;
import com.agilify.core.websocket.DocWebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final CrdtDocWebSocketHandler crdtDocWebSocketHandler;
    private final DocWebSocketAuthInterceptor docWebSocketAuthInterceptor;

    @Value("${websocket.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(crdtDocWebSocketHandler, "/ws/docs/{docId}")
                .addInterceptors(docWebSocketAuthInterceptor)
                .setAllowedOrigins(allowedOrigins.split(","));
    }
}
