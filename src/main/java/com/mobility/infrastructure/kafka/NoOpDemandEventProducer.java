package com.mobility.infrastructure.kafka;

import com.mobility.infrastructure.kafka.events.DemandCancelledEvent;
import com.mobility.infrastructure.kafka.events.DemandCreatedEvent;
import com.mobility.infrastructure.kafka.events.DemandExpiredBatchEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * No-op fallback when Kafka is not configured (local dev / spring.kafka.enabled != true).
 * UserService injects DemandEventProducer via @RequiredArgsConstructor —
 * this stub satisfies that dependency without needing a running Kafka broker.
 */
@Component
@Slf4j
@ConditionalOnMissingBean(DemandEventProducer.class)
public class NoOpDemandEventProducer extends DemandEventProducer {

    public NoOpDemandEventProducer() {
        super(null);
    }

    @Override
    public void publishDemandCreated(DemandCreatedEvent event) {
        log.debug("[NoOp] DemandCreated corridorCode={} seg={}",
                event.getCorridorCode(), event.getSegmentIndex());
    }

    @Override
    public void publishDemandCancelled(DemandCancelledEvent event) {
        log.debug("[NoOp] DemandCancelled corridorCode={} seg={}",
                event.getCorridorCode(), event.getSegmentIndex());
    }

    @Override
    public void publishDemandExpiredBatch(DemandExpiredBatchEvent event) {
        log.debug("[NoOp] DemandExpiredBatch corridorCode={} count={}",
                event.getCorridorCode(), event.getExpiredCount());
    }
}