package com.mobility.infrastructure.websocket;

import com.mobility.domain.driver.dto.CorridorHeatmapResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketBroadcastService {

    private final RedisTemplate<String, String> redisTemplate;
    private final DemandWebSocketHandler        localHandler;
    private final ObjectMapper objectMapper;

    private static final String WS_CHANNEL_PREFIX = "ws:corridor:";

    /**
     * Called by the scheduler pod after computing a heatmap.
     * Publishes to Redis Pub/Sub — ALL pods receive this and push
     * to their locally-connected drivers.
     */
    public void broadcastHeatmap(String corridorCode,
                                 CorridorHeatmapResponse heatmap) {
        try {
            String channel = WS_CHANNEL_PREFIX + corridorCode;
            String payload = objectMapper.writeValueAsString(heatmap);
            redisTemplate.convertAndSend(channel, payload);
        } catch (Exception e) {
            log.error("Failed to broadcast heatmap for corridor={}", corridorCode, e);
        }
    }

    /**
     * Each pod subscribes to all corridor channels on startup.
     * When a message arrives, it pushes to locally-held sessions only.
     */
    @Bean
    public RedisMessageListenerContainer redisListenerContainer(
            RedisConnectionFactory factory) {

        RedisMessageListenerContainer container =
                new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);

        // Subscribe to all corridor heatmap channels
        container.addMessageListener(
                (message, pattern) -> {
                    String channel = new String(message.getChannel());
                    String corridorCode = channel.replace(WS_CHANNEL_PREFIX, "");
                    try {
                        CorridorHeatmapResponse heatmap = objectMapper
                                .readValue(message.getBody(), CorridorHeatmapResponse.class);
                        // Push only to sessions held by THIS pod
                        localHandler.pushCorridorHeatmap(corridorCode, heatmap);
                    } catch (Exception e) {
                        log.error("Failed to relay WS message channel={}", channel, e);
                    }
                },
                new PatternTopic(WS_CHANNEL_PREFIX + "*")
        );

        return container;
    }
}