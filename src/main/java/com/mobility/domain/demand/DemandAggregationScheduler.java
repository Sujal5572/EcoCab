package com.mobility.domain.demand;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobility.domain.corridor.Corridor;
import com.mobility.domain.corridor.CorridorRepository;
import com.mobility.domain.driver.dto.CorridorHeatmapResponse;
import com.mobility.infrastructure.websocket.DemandWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// domain/demand/DemandAggregationScheduler.java
@Component
@RequiredArgsConstructor
@Slf4j
public class DemandAggregationScheduler {

    private final CorridorRepository corridorRepository;
    private final DemandAggregateRepository aggregateRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final DemandWebSocketHandler webSocketHandler;

    /**
     * Every 30 seconds:
     *  1. Read all segment counters from Redis for every active corridor
     *  2. Build a CorridorHeatmapResponse
     *  3. Persist a DemandAggregate row to TimescaleDB (for analytics)
     *  4. Write the full heatmap JSON to Redis (for driver app initial load)
     *  5. Push the heatmap over WebSocket to all connected drivers
     *
     * ShedLock ensures only ONE instance runs this in a multi-pod deployment.
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    @SchedulerLock(name = "demandAggregationJob", lockAtMostFor = "25s", lockAtLeastFor = "20s")
    public void aggregateAndBroadcast() {
        Instant windowStart = Instant.now().minusSeconds(30);
        Instant windowEnd   = Instant.now();

        List<Corridor> activeCorridors = corridorRepository.findAllActive();

        for (Corridor corridor : activeCorridors) {
            try {
                processCorridorAggregation(corridor, windowStart, windowEnd);
            } catch (Exception e) {
                // One corridor failure should NOT stop other corridors
                log.error("Aggregation failed for corridor={}", corridor.getCode(), e);
            }
        }
    }

    private void processCorridorAggregation(Corridor corridor,
                                            Instant windowStart,
                                            Instant windowEnd) {
        String corridorCode    = corridor.getCode();
        int    totalSegments   = corridor.getTotalSegments();

        List<DemandAggregateResponse.SegmentDensity> segmentDensities = new ArrayList<>();
        List<DemandAggregate> aggregatesToPersist = new ArrayList<>();

        for (int i = 0; i < totalSegments; i++) {
            String countKey   = "demand:" + corridorCode + ":seg:" + i + ":count";
            String driversKey = "demand:" + corridorCode + ":seg:" + i + ":drivers";

            String countStr   = redisTemplate.opsForValue().get(countKey);
            String driversStr = redisTemplate.opsForValue().get(driversKey);

            int demandCount  = countStr   != null ? Integer.parseInt(countStr)   : 0;
            int driverCount  = driversStr != null ? Integer.parseInt(driversStr) : 0;

            double ratio = driverCount == 0
                    ? demandCount          // no driver → all demand is unserved
                    : (double) demandCount / driverCount;

            String densityLevel = classifyDensity(ratio);

            segmentDensities.add(CorridorHeatmapResponse.SegmentDensity.builder()
                    .segmentIndex(i)
                    .demandCount(demandCount)
                    .driverCount(driverCount)
                    .demandPerDriver(ratio)
                    .densityLevel(densityLevel)
                    .build());

            // Only persist non-zero windows to TimescaleDB — saves storage
            if (demandCount > 0 || driverCount > 0) {
                aggregatesToPersist.add(DemandAggregate.builder()
                        .corridorId(corridor.getId())
                        .segmentIndex(i)
                        .activeDemandCount(demandCount)
                        .driversInSegment(driverCount)
                        .windowStart(windowStart)
                        .windowEnd(windowEnd)
                        .build());
            }
        }

        // Batch persist to TimescaleDB (single INSERT ... VALUES (...),(...))
        if (!aggregatesToPersist.isEmpty()) {
            aggregateRepository.saveAll(aggregatesToPersist);
        }

        // Write full heatmap snapshot to Redis (for driver app cold start)
        CorridorHeatmapResponse heatmap = CorridorHeatmapResponse.builder()
                .corridorId(corridor.getId())
                .corridorCode(corridorCode)
                .totalSegments(totalSegments)
                .segments(segmentDensities)
                .build();

        String heatmapJson = toJson(heatmap);
        redisTemplate.opsForValue().set(
                "heatmap:" + corridorCode,
                heatmapJson,
                Duration.ofMinutes(2)  // stale if scheduler dies
        );

        // Push over WebSocket to all drivers subscribed to this corridor
        webSocketHandler.pushCorridorHeatmap(corridorCode, heatmap);

        log.debug("Aggregated corridor={} segments={} persisted={}",
                corridorCode, totalSegments, aggregatesToPersist.size());
    }

    /**
     * Demand density classification.
     * These thresholds are tunable via config — do NOT hardcode in prod.
     *
     * ratio = demand_count / driver_count
     *   < 1.0  → LOW     (more drivers than demand — drivers should spread out)
     *   1-3    → MEDIUM  (healthy balance)
     *   3-6    → HIGH    (drivers should move toward this segment)
     *   > 6    → SURGE   (all nearby drivers should converge)
     */
    private String classifyDensity(double ratio) {
        if (ratio < 1.0) return "LOW";
        if (ratio < 3.0) return "MEDIUM";
        if (ratio < 6.0) return "HIGH";
        return "SURGE";
    }

    private String toJson(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
}
