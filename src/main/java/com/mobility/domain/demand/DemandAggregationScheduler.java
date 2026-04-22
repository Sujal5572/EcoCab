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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DemandAggregationScheduler {

    private final CorridorRepository           corridorRepository;
    private final DemandAggregateRepository    aggregateRepository;  // created below
    private final RedisTemplate<String, String> redisTemplate;
    private final DemandWebSocketHandler        webSocketHandler;

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
                log.error("Aggregation failed for corridor={}", corridor.getCode(), e);
            }
        }
    }

    private void processCorridorAggregation(Corridor corridor,
                                            Instant windowStart,
                                            Instant windowEnd) {
        String corridorCode  = corridor.getCode();
        int    totalSegments = corridor.getTotalSegments();

        // FIX: was DemandAggregateResponse.SegmentDensity — that class doesn't exist.
        // CorridorHeatmapResponse.SegmentDensity is the correct class.
        List<CorridorHeatmapResponse.SegmentDensity> segmentDensities = new ArrayList<>();
        List<DemandAggregate> aggregatesToPersist = new ArrayList<>();

        for (int i = 0; i < totalSegments; i++) {
            String countKey   = "demand:" + corridorCode + ":seg:" + i + ":count";
            String driversKey = "demand:" + corridorCode + ":seg:" + i + ":drivers";

            String countStr   = redisTemplate.opsForValue().get(countKey);
            String driversStr = redisTemplate.opsForValue().get(driversKey);

            int demandCount = countStr   != null ? Integer.parseInt(countStr)   : 0;
            int driverCount = driversStr != null ? Integer.parseInt(driversStr) : 0;

            double ratio = driverCount == 0 ? demandCount
                    : (double) demandCount / driverCount;

            String densityLevel = classifyDensity(ratio);

            segmentDensities.add(CorridorHeatmapResponse.SegmentDensity.builder()
                    .segmentIndex(i)
                    .demandCount(demandCount)
                    .driverCount(driverCount)
                    .demandPerDriver(ratio)
                    .densityLevel(densityLevel)
                    .build());

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

        if (!aggregatesToPersist.isEmpty()) {
            aggregateRepository.saveAll(aggregatesToPersist);
        }

        CorridorHeatmapResponse heatmap = CorridorHeatmapResponse.builder()
                .corridorId(corridor.getId())
                .corridorCode(corridorCode)
                .totalSegments(totalSegments)
                .segments(segmentDensities)
                .build();

        String heatmapJson = toJson(heatmap);
        // FIX: Duration import was missing
        redisTemplate.opsForValue().set(
                "heatmap:" + corridorCode,
                heatmapJson,
                Duration.ofMinutes(2)
        );

        webSocketHandler.pushCorridorHeatmap(corridorCode, heatmap);

        log.debug("Aggregated corridor={} segments={} persisted={}",
                corridorCode, totalSegments, aggregatesToPersist.size());
    }

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