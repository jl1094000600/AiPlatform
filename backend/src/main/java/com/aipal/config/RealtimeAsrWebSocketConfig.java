package com.aipal.config;

import com.aipal.service.RealtimeAsrWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class RealtimeAsrWebSocketConfig implements WebSocketConfigurer {
    private final RealtimeAsrWebSocketHandler handler;

    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/asr/realtime")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }
}
