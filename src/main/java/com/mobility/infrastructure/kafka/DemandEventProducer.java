package com.mobility.infrastructure.kafka;

import com.mobility.infrastructure.kafka.events.DemandCancelledEvent;
import com.mobility.infrastructure.kafka.events.DemandCreatedEvent;
import com.mobility.infrastructure.kafka.events.DemandExpiredBatchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// Real producer — active when spring.kafka.enabled=true (default when Kafka is reachable)
@Component
@Primary
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class DemandEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

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