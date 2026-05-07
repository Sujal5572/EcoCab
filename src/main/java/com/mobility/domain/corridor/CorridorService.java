package com.mobility.domain.corridor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobility.domain.driver.dto.CorridorHeatmapResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorridorService {

    private final CorridorRepository corridorRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public List<Corridor> listActive() {
        return corridorRepository.findAllActive();
    }

    public Corridor getDetail(UUID id) {
        return corridorRepository.findByIdWithSegments(id)
                .orElseThrow(() -> new RuntimeException("Corridor not found: " + id));
    }

    public Corridor create(Corridor corridor) {
        return corridorRepository.save(corridor);
    }

    /**
     * Returns live demand heatmap for a corridor.
     * 1. Try Redis cache key "heatmap:{code}" — written by DemandAggregationScheduler every 30s
     * 2. If cache miss, build it live from individual Redis counter keys
     */
//    public CorridorHeatmapResponse getLiveDemand(UUID id) {
//        Corridor corridor = corridorRepository.findByIdWithSegments(id)
//                .orElseThrow(() -> new RuntimeException("Corridor not found: " + id));
//
//        String cacheKey = "heatmap:" + corridor.getCode();
//
//        // Try cached heatmap first (scheduler writes this every 30s)
//        String cached = redisTemplate.opsForValue().get(cacheKey);
//        if (cached != null) {
//            try {
//                return objectMapper.readValue(cached, CorridorHeatmapResponse.class);
//            } catch (Exception e) {
//                log.warn("Failed to parse cached heatmap for {}, rebuilding live", corridor.getCode());
//            }
//        }
//
//        // Cache miss — build live from Redis counter keys
//        return buildLiveHeatmap(corridor);
//    }
    public CorridorHeatmapResponse getLiveDemand(UUID id) {
        Corridor corridor = corridorRepository.findByIdWithSegments(id)
                .orElseThrow(() -> new RuntimeException("Corridor not found: " + id));

        String cacheKey = "heatmap:" + corridor.getCode();

        // ✅ 1. Try cached heatmap
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                CorridorHeatmapResponse response =
                        objectMapper.readValue(cached, CorridorHeatmapResponse.class);

                // ⭐ ENRICH RESPONSE
                return CorridorHeatmapResponse.builder()
                        .corridorId(response.getCorridorId())
                        .corridorCode(response.getCorridorCode())
                        .corridorName(corridor.getName()) // ✅ ADD
                        .totalSegments(response.getTotalSegments())
                        .segments(response.getSegments())
                        .build();

            } catch (Exception e) {
                log.warn("Failed to parse cached heatmap for {}, rebuilding live", corridor.getCode());
            }
        }

        // ✅ 2. Cache miss → build live
        CorridorHeatmapResponse response = buildLiveHeatmap(corridor);

        // ⭐ ENRICH HERE ALSO
        return CorridorHeatmapResponse.builder()
                .corridorId(response.getCorridorId())
                .corridorCode(response.getCorridorCode())
                .corridorName(corridor.getName()) // ✅ ADD
                .totalSegments(response.getTotalSegments())
                .segments(response.getSegments())
                .build();
    }

    private CorridorHeatmapResponse buildLiveHeatmap(Corridor corridor) {
        String code = corridor.getCode();
        int total   = corridor.getTotalSegments();

        List<CorridorHeatmapResponse.SegmentDensity> segments = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            String cStr = redisTemplate.opsForValue().get("demand:" + code + ":seg:" + i + ":count");
            String dStr = redisTemplate.opsForValue().get("demand:" + code + ":seg:" + i + ":drivers");

            int demand  = cStr != null ? Integer.parseInt(cStr) : 0;
            int drivers = dStr != null ? Integer.parseInt(dStr) : 0;
            double ratio = drivers == 0 ? demand : (double) demand / drivers;

            segments.add(CorridorHeatmapResponse.SegmentDensity.builder()
                    .segmentIndex(i)
                    .demandCount(demand)
                    .driverCount(drivers)
                    .demandPerDriver(ratio)
                    .densityLevel(classifyDensity(ratio))
                    .build());
        }

        return CorridorHeatmapResponse.builder()
                .corridorId(corridor.getId())
                .corridorCode(code)
                .totalSegments(total)
                .segments(segments)
                .build();
    }

    private String classifyDensity(double ratio) {
        if (ratio == 0)  return "EMPTY";
        if (ratio < 1.0) return "LOW";
        if (ratio < 3.0) return "MEDIUM";
        if (ratio < 6.0) return "HIGH";
        return "SURGE";
    }
}