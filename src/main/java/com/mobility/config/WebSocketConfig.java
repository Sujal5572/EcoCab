package com.mobility.config;

// FIX: @EnableWebSocket and WebSocketConfigurer imports were missing
import com.mobility.infrastructure.websocket.DemandWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final DemandWebSocketHandler demandWebSocketHandler;

    public WebSocketConfig(DemandWebSocketHandler demandWebSocketHandler) {
        this.demandWebSocketHandler = demandWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(demandWebSocketHandler, "/ws/driver/{driverId}")
                .setAllowedOrigins("*");
        registry.addHandler(demandWebSocketHandler, "/ws/ops/{corridorId}")
                .setAllowedOrigins("*");
    }
}