package com.mobility.domain.demand;

import com.mobility.infrastructure.kafka.DemandEventProducer;
import com.mobility.infrastructure.kafka.events.DemandExpiredBatchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

// domain/demand/DemandExpiryCleanupJob.java
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("kafka")
public class DemandExpiryCleanupJob {

    private final DemandSignalRepository demandSignalRepository;
    private final DemandEventProducer demandEventProducer;

    /**
     * Runs every 5 minutes.
     * Marks expired signals in PostgreSQL, then fires Demand Expired events
     * to Kafka — which the consumer uses to DECRBY the Redis counters.
     *
     * This is the self-healing mechanism:
     * If a passenger never cancels, their demand signal expires after 30 min
     * and Redis is decremented automatically.
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @SchedulerLock(name = "demandExpiryCleanupJob", lockAtMostFor = "4m", lockAtLeastFor = "3m")
    public void expireStaleSignals() {
        // Uses a native query to UPDATE + RETURNING in one round trip
        // Grouped by corridor + segment to batch the Kafka events
        List<ExpiredSignalSummary> expired =
                demandSignalRepository.expireAndSummarize(Instant.now());

        if (expired.isEmpty()) {
            log.debug("No expired demand signals found");
            return;
        }

        log.info("Expiring {} corridor-segment buckets of demand signals", expired.size());

        for (ExpiredSignalSummary summary : expired) {
            demandEventProducer.publishDemandExpiredBatch(
                    DemandExpiredBatchEvent.builder()
                            .corridorCode(summary.getCorridorCode())
                            .segmentIndex(summary.getSegmentIndex())
                            .expiredCount(summary.getExpiredCount())
                            .build()
            );
        }
    }
}