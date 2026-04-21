package com.mobility.infrastructure.websocket;

import com.mobility.domain.driver.dto.CorridorHeatmapResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

// infrastructure/websocket/DemandWebSocketHandler.java
@Component
@RequiredArgsConstructor
@Slf4j
public class DemandWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    // corridorCode → Set of active WebSocket sessions for drivers on that corridor
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>>
            corridorSessions = new ConcurrentHashMap<>();

    // driverId → corridorCode (for cleanup on disconnect)
    private final ConcurrentHashMap<String, String>
            sessionCorridorMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String corridorCode = extractCorridorCode(session);
        if (corridorCode == null) {
            closeQuietly(session);
            return;
        }

        corridorSessions
                .computeIfAbsent(corridorCode, k -> new CopyOnWriteArraySet<>())
                .add(session);

        sessionCorridorMap.put(session.getId(), corridorCode);

        log.info("Driver WS connected session={} corridor={}", session.getId(), corridorCode);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String corridorCode = sessionCorridorMap.remove(session.getId());
        if (corridorCode != null) {
            Set<WebSocketSession> sessions = corridorSessions.get(corridorCode);
            if (sessions != null) sessions.remove(session);
        }
        log.info("Driver WS disconnected session={}", session.getId());
    }

    /**
     * Pushes a single-segment update immediately on demand INCR/DECR.
     * Small payload — only the changed segment, not the full heatmap.
     */
    public void pushSegmentUpdate(String corridorCode, int segmentIndex, int newCount) {
        Map<String, Object> payload = Map.of(
                "type",         "SEGMENT_UPDATE",
                "segmentIndex", segmentIndex,
                "demandCount",  newCount,
                "timestamp",    Instant.now().toEpochMilli()
        );
        broadcastToCorridor(corridorCode, payload);
    }

    /**
     * Pushes the full corridor heatmap every 30 seconds.
     * Drivers use this for their full-screen density map.
     */
    public void pushCorridorHeatmap(String corridorCode, CorridorHeatmapResponse heatmap) {
        Map<String, Object> payload = Map.of(
                "type",    "HEATMAP_UPDATE",
                "heatmap", heatmap,
                "timestamp", Instant.now().toEpochMilli()
        );
        broadcastToCorridor(corridorCode, payload);
    }

    private void broadcastToCorridor(String corridorCode, Object payload) {
        Set<WebSocketSession> sessions = corridorSessions.get(corridorCode);
        if (sessions == null || sessions.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize WebSocket payload", e);
            return;
        }

        TextMessage message = new TextMessage(json);
        List<WebSocketSession> deadSessions = new ArrayList<>();

        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        // synchronized — WebSocketSession is NOT thread-safe
                        session.sendMessage(message);
                    }
                } else {
                    deadSessions.add(session);
                }
            } catch (IOException e) {
                log.warn("Failed to send WS message session={}", session.getId(), e);
                deadSessions.add(session);
            }
        }

        // Prune dead sessions
        if (!deadSessions.isEmpty()) {
            sessions.removeAll(deadSessions);
        }
    }

    // Extract corridorCode from URI: /ws/driver/{driverId}?corridor=GG_GM
    private String extractCorridorCode(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=");
            if (kv.length == 2 && "corridor".equals(kv[0])) return kv[1];
        }
        return null;
    }
    // FIX: MetricsConfig calls this — method was missing entirely
    public int totalConnections() {
        return corridorSessions.values().stream()
                .mapToInt(java.util.Set::size)
                .sum();
    }

    private void closeQuietly(WebSocketSession session) {
        try { session.close(CloseStatus.BAD_DATA); } catch (Exception ignored) {}
    }
}