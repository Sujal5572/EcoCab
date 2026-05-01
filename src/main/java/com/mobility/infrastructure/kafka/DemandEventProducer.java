package com.mobility.infrastructure.kafka;

import com.mobility.infrastructure.kafka.events.DemandCancelledEvent;
import com.mobility.infrastructure.kafka.events.DemandCreatedEvent;
import com.mobility.infrastructure.kafka.events.DemandExpiredBatchEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * FIX: Removed @ConditionalOnProperty and @Primary.
 *
 * Root cause of the crash:
 *   NoOpDemandEventProducer extends DemandEventProducer.
 *   When spring.kafka.enabled=false, Spring skips creating DemandEventProducer.
 *   NoOpDemandEventProducer then tries to extend a class that isn't a bean —
 *   Spring still cannot satisfy the DemandEventProducer injection in UserService.
 *
 * Fix strategy:
 *   DemandEventProducer is ALWAYS a bean.
 *   KafkaTemplate is injected as Optional — if Kafka is disabled/unavailable,
 *   the template is null and we log.debug instead of crashing.
 *   This makes the producer safe with OR without Kafka running.
 */
@Component
@Slf4j
public class DemandEventProducer {

    // @Autowired(required=false) — if KafkaTemplate bean doesn't exist
    // (when KafkaAutoConfiguration is excluded), this is simply null.
    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void publishDemandCreated(DemandCreatedEvent event) {
        send(KafkaConfig.TOPIC_DEMAND_CREATED, event.getCorridorCode(), event);
    }

    public void publishDemandCancelled(DemandCancelledEvent event) {
        send(KafkaConfig.TOPIC_DEMAND_CANCELLED, event.getCorridorCode(), event);
    }

    public void publishDemandExpiredBatch(DemandExpiredBatchEvent event) {
        send(KafkaConfig.TOPIC_DEMAND_EXPIRED, event.getCorridorCode(), event);
    }

    private void send(String topic, String key, Object payload) {
        if (kafkaTemplate == null) {
            // Kafka not configured — log and skip. No crash.
            log.debug("[NoOp] Kafka disabled. Would send to topic={} key={} payload={}",
                    topic, key, payload.getClass().getSimpleName());
            return;
        }
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish to topic={} key={}", topic, key, ex);
                    } else {
                        log.debug("Published to topic={} partition={} offset={}",
                                topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}