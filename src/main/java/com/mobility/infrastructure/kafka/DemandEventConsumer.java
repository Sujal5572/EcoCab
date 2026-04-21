package com.mobility.infrastructure.kafka;

import com.mobility.infrastructure.kafka.events.DemandCancelledEvent;
import com.mobility.infrastructure.kafka.events.DemandCreatedEvent;
import com.mobility.infrastructure.kafka.events.DemandExpiredBatchEvent;
import com.mobility.infrastructure.websocket.DemandWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

// infrastructure/kafka/DemandEventConsumer.java
@Component
@RequiredArgsConstructor
@Slf4j
public class DemandEventConsumer {

    private final RedisTemplate<String, String> redisTemplate;
    private final DemandWebSocketHandler webSocketHandler;

    private static final Duration COUNT_TTL  = Duration.ofMinutes(35); // > demand signal TTL
    private static final Duration DRIVER_TTL = Duration.ofSeconds(90);

    // ----------------------------------------------------------------
    // DEMAND CREATED — increment segment counter atomically
    // ----------------------------------------------------------------
    @KafkaListener(
            topics   = KafkaConfig.TOPIC_DEMAND_CREATED,
            groupId  = KafkaConfig.GROUP_DEMAND_SERVICE,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDemandCreated(
            @Payload DemandCreatedEvent event,
            Acknowledgment ack) {
        try {
            String countKey = buildCountKey(event.getCorridorCode(), event.getSegmentIndex());

            // INCR is atomic — safe under concurrent consumers
            Long newCount = redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, COUNT_TTL);

            log.info("Demand INCR corridor={} seg={} newCount={}",
                    event.getCorridorCode(), event.getSegmentIndex(), newCount);

            // Immediately notify drivers on this corridor about the count change.
            // This is a lightweight push — just the changed segment, not the full heatmap.
            if (newCount != null) {
                webSocketHandler.pushSegmentUpdate(
                        event.getCorridorCode(),
                        event.getSegmentIndex(),
                        newCount.intValue());
            }

            ack.acknowledge(); // commit offset only after Redis write succeeds

        } catch (Exception e) {
            log.error("Failed processing DemandCreatedEvent signalId={}",
                    event.getSignalId(), e);
            // Do NOT acknowledge — Kafka will redeliver. Dead letter after 3 retries.
            throw e;
        }
    }

    // ----------------------------------------------------------------
    // DEMAND CANCELLED — decrement, floor at 0
    // ----------------------------------------------------------------
    @KafkaListener(
            topics   = KafkaConfig.TOPIC_DEMAND_CANCELLED,
            groupId  = KafkaConfig.GROUP_DEMAND_SERVICE,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDemandCancelled(
            @Payload DemandCancelledEvent event,
            Acknowledgment ack) {
        try {
            String countKey = buildCountKey(event.getCorridorCode(), event.getSegmentIndex());

            // Lua script — atomic DECR with floor at 0.
            // Prevents negative counts if events arrive out of order.
            Long newCount = redisTemplate.execute(
                    DECR_FLOOR_ZERO_SCRIPT,
                    Collections.singletonList(countKey)
            );

            log.info("Demand DECR corridor={} seg={} newCount={}",
                    event.getCorridorCode(), event.getSegmentIndex(), newCount);

            if (newCount != null) {
                webSocketHandler.pushSegmentUpdate(
                        event.getCorridorCode(),
                        event.getSegmentIndex(),
                        newCount.intValue());
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed processing DemandCancelledEvent signalId={}",
                    event.getSignalId(), e);
            throw e;
        }
    }

    // ----------------------------------------------------------------
    // DEMAND EXPIRED BATCH — bulk decrement from cleanup job
    // ----------------------------------------------------------------
    @KafkaListener(
            topics   = KafkaConfig.TOPIC_DEMAND_EXPIRED,
            groupId  = KafkaConfig.GROUP_DEMAND_SERVICE,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDemandExpiredBatch(
            @Payload DemandExpiredBatchEvent event,
            Acknowledgment ack) {
        try {
            String countKey = buildCountKey(event.getCorridorCode(), event.getSegmentIndex());

            // Lua script — DECRBY with floor at 0
            Long newCount = redisTemplate.execute(
                    DECRBY_FLOOR_ZERO_SCRIPT,
                    Collections.singletonList(countKey),
                    String.valueOf(event.getExpiredCount())
            );

            log.info("Demand EXPIRE_BATCH corridor={} seg={} expiredCount={} newCount={}",
                    event.getCorridorCode(), event.getSegmentIndex(),
                    event.getExpiredCount(), newCount);

            if (newCount != null) {
                webSocketHandler.pushSegmentUpdate(
                        event.getCorridorCode(),
                        event.getSegmentIndex(),
                        newCount.intValue());
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed processing DemandExpiredBatchEvent", e);
            throw e;
        }
    }

    // ----------------------------------------------------------------
    // Lua Scripts — loaded once, executed atomically in Redis
    // ----------------------------------------------------------------

    // DECR but never below 0
    private static final RedisScript<Long> DECR_FLOOR_ZERO_SCRIPT =
            RedisScript.of(
                    "local v = redis.call('GET', KEYS[1]) " +
                            "if not v or tonumber(v) <= 0 then return 0 end " +
                            "return redis.call('DECR', KEYS[1])",
                    Long.class
            );

    // DECRBY n but never below 0
    private static final RedisScript<Long> DECRBY_FLOOR_ZERO_SCRIPT =
            RedisScript.of(
                    "local v = redis.call('GET', KEYS[1]) " +
                            "if not v then return 0 end " +
                            "local cur = tonumber(v) " +
                            "local dec = tonumber(ARGV[1]) " +
                            "local nv = math.max(0, cur - dec) " +
                            "redis.call('SET', KEYS[1], nv) " +
                            "return nv",
                    Long.class
            );

    private String buildCountKey(String corridorCode, int segmentIndex) {
        return String.format("demand:%s:seg:%d:count", corridorCode, segmentIndex);
    }
}