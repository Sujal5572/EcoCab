package com.mobility.domain.driver;

import com.mobility.common.BusinessException;
import com.mobility.common.EntityNotFoundException;
import com.mobility.domain.corridor.Corridor;
import com.mobility.domain.corridor.CorridorRepository;
import com.mobility.domain.corridor.CorridorSegment;
import com.mobility.domain.driver.dto.LocationUpdateRequest;
import com.mobility.domain.driver.dto.LocationUpdateResponse;
import com.mobility.domain.driver.guidance.GuidanceContext;
import com.mobility.domain.driver.guidance.GuidanceEngine;
import com.mobility.domain.driver.guidance.SegmentSnapshot;
import jakarta.persistence.QueryTimeoutException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// domain/driver/DriverService.java
@Service
@RequiredArgsConstructor
@Slf4j
public class DriverService {

    private final DriverRepository              driverRepository;
    private final CorridorRepository corridorRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final GuidanceEngine guidanceEngine;

    private static final Duration DRIVER_LOC_TTL   = Duration.ofSeconds(90);
    private static final Duration SEGMENT_SCORE_TTL = Duration.ofSeconds(90);

    /**
     * Called every 10 seconds per online driver.
     * This is the hottest write path after demand signal creation.
     *
     * Steps:
     *  1. Validate driver is ONLINE on a corridor
     *  2. Resolve which segment they are in (geometry)
     *  3. Update Redis: driver location + segment driver-count
     *  4. Update PostgreSQL: driver current_segment (async, best-effort)
     *  5. Run GuidanceEngine
     *  6. Return guidance inline to driver
     */
    @Transactional
    public LocationUpdateResponse updateLocation(UUID driverId,
                                                 LocationUpdateRequest req) {
        try {
            return doUpdateLocation(driverId, req);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            // Redis is down — degrade gracefully
            log.error("Redis unavailable for driver={}, falling back to DB-only mode",
                    driverId, e);
            return degradedLocationUpdate(driverId, req);
        }
    }
    private LocationUpdateResponse degradedLocationUpdate(UUID driverId,
                                                          LocationUpdateRequest req) {
        // Best-effort DB write — doesn't depend on Redis
        try {
            driverRepository.updateLocationAndSegment(driverId,
                    req.getLatitude(), req.getLongitude(), 0);
        } catch (Exception e) {
            log.error("DB write also failed for driver={}", driverId, e);
        }

        return LocationUpdateResponse.builder()
                .currentSegmentIndex(0)
                .demandAhead(0)
                .driversAhead(0)
                .guidance("CONTINUE")      // safe default
                .recommendedSegment(1)
                .guidanceReason("Guidance temporarily unavailable. Continue at normal pace.")
                .urgency("NONE")
                .build();
    }

//        Driver driver = driverRepository.findById(driverId)
//                .orElseThrow(() -> new EntityNotFoundException("Driver", driverId));
//
//        if (driver.getStatus() == Driver.DriverStatus.OFFLINE) {
//            throw new BusinessException("Driver is offline. Go online first.");
//        }
//
//        Corridor corridor = driver.getCurrentCorridor();
//        if (corridor == null) {
//            throw new BusinessException("Driver has no active corridor assigned.");
//        }
//
//        // ── 1. Resolve segment from lat/lng ──────────────────────────
//        int newSegment = resolveSegmentIndex(
//                corridor, req.getLatitude(), req.getLongitude());
//
//        int oldSegment = driver.getCurrentSegmentIndex() != null
//                ? driver.getCurrentSegmentIndex() : newSegment;
//
//        // ── 2. Update Redis driver state ─────────────────────────────
//        updateDriverInRedis(driver, corridor, oldSegment, newSegment,
//                req.getLatitude(), req.getLongitude());
//
//        // ── 3. Update PostgreSQL (non-blocking best-effort) ───────────
//        // We do NOT block on this — PostgreSQL update can lag by 1 ping cycle
//        driverRepository.updateLocationAndSegment(
//                driverId, req.getLatitude(), req.getLongitude(), newSegment);
//
//        // ── 4. Build GuidanceContext from Redis heatmap ───────────────
//        GuidanceContext context = buildGuidanceContext(
//                driver, corridor, newSegment, req);
//
//        // ── 5. Compute guidance ───────────────────────────────────────
//        GuidanceResult guidance = guidanceEngine.compute(context);
//
//        log.debug("Driver {} seg {} → {} action={} urgency={}",
//                driverId, oldSegment, newSegment,
//                guidance.getAction(), guidance.getUrgency());
//
//        return LocationUpdateResponse.builder()
//                .currentSegmentIndex(newSegment)
//                .demandAhead(guidance.getDemandAhead())
//                .driversAhead(driversAhead(corridor.getCode(), newSegment,
//                        corridor.getTotalSegments()))
//                .guidance(guidance.getAction().name())
//                .recommendedSegment(guidance.getRecommendedSegment())
//                .guidanceReason(guidance.getReason())
//                .urgency(guidance.getUrgency().name())
//                .build();
//    }

    // ---------------------------------------------------------------
    // Redis update — atomic segment driver-count swap
    // ---------------------------------------------------------------
    private void updateDriverInRedis(Driver driver, Corridor corridor,
                                     int oldSegment, int newSegment,
                                     double lat, double lng) {
        String code = corridor.getCode();

        // Live location key (TTL auto-expires offline drivers)
        redisTemplate.opsForValue().set(
                "driver:" + driver.getId() + ":location",
                lat + "," + lng + "," + newSegment,
                DRIVER_LOC_TTL
        );

        if (oldSegment != newSegment) {
            // Driver crossed into a new segment — update both counters atomically
            redisTemplate.execute(SWAP_DRIVER_SEGMENT_SCRIPT,
                    Arrays.asList(
                            "demand:" + code + ":seg:" + oldSegment + ":drivers",
                            "demand:" + code + ":seg:" + newSegment + ":drivers"
                    )
            );
        } else {
            // Just refresh TTL on current segment counter
            String key = "demand:" + code + ":seg:" + newSegment + ":drivers";
            redisTemplate.opsForValue().setIfAbsent(key, "0");
            redisTemplate.expire(key, SEGMENT_SCORE_TTL);
        }
    }

    // Atomic: DECR old segment (floor 0), INCR new segment
    private static final RedisScript<Void> SWAP_DRIVER_SEGMENT_SCRIPT =
            RedisScript.of(
                    "local old = redis.call('GET', KEYS[1]) " +
                            "if old and tonumber(old) > 0 then redis.call('DECR', KEYS[1]) end " +
                            "redis.call('INCR', KEYS[2]) " +
                            "return nil",
                    Void.class
            );

    // ---------------------------------------------------------------
    // Build GuidanceContext from Redis (no DB call in hot path)
    // ---------------------------------------------------------------

    // domain/driver/DriverService.java — replace buildGuidanceContext
    private GuidanceContext buildGuidanceContext(Driver driver,
                                                 Corridor corridor,
                                                 int currentSegment,
                                                 LocationUpdateRequest req) {
        String code      = corridor.getCode();
        int    totalSegs = corridor.getTotalSegments();

        // Pipeline all segment reads in ONE round-trip to Redis
        // Before: totalSegs × 2 = 16 network calls
        // After:  1 network call
        List<Object> pipelineResults = redisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    for (int i = 0; i < totalSegs; i++) {
                        byte[] countKey   = ("demand:" + code + ":seg:" + i + ":count")  .getBytes();
                        byte[] driversKey = ("demand:" + code + ":seg:" + i + ":drivers").getBytes();
                        connection.stringCommands().get(countKey);
                        connection.stringCommands().get(driversKey);
                    }
                    return null;
                }
        );

        List<SegmentSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < totalSegs; i++) {
            // Results are interleaved: [count0, drivers0, count1, drivers1, ...]
            String countStr   = (String) pipelineResults.get(i * 2);
            String driversStr = (String) pipelineResults.get(i * 2 + 1);

            snapshots.add(SegmentSnapshot.builder()
                    .index(i)
                    .demandCount(countStr   != null ? Integer.parseInt(countStr)   : 0)
                    .driverCount(driversStr != null ? Integer.parseInt(driversStr) : 0)
                    .build());
        }

        return GuidanceContext.builder()
                .driverId(driver.getId())
                .corridorId(corridor.getId())
                .corridorCode(code)
                .currentSegmentIndex(currentSegment)
                .totalSegments(totalSegs)
                .maxDriverCapacity(DEFAULT_MAX_DRIVERS_PER_SEGMENT)
                .speedKmh(req.getSpeedKmh() != null ? req.getSpeedKmh() : 0.0)
                .segments(snapshots)
                .build();
    }
    // ---------------------------------------------------------------
    // Segment resolution — which segment is this lat/lng in?
    // ---------------------------------------------------------------

    /**
     * Uses a simple nearest-midpoint approach.
     * In production, replace with ST_LineLocatePoint (PostGIS) for
     * true along-route snapping, especially for curved corridors.
     *
     * For a straight corridor like Gai Ghat → Gandhi Maidan (≈ 4 km),
     * the simple approach is accurate enough.
     */
    public int resolveSegmentIndex(Corridor corridor,
                                   double lat, double lng) {
        List<CorridorSegment> segments = corridor.getSegments();
        if (segments == null || segments.isEmpty()) return 0;

        double minDist  = Double.MAX_VALUE;
        int    bestIdx  = 0;

        for (CorridorSegment seg : segments) {
            double segLat = seg.getSegmentStart().getY();
            double segLng = seg.getSegmentStart().getX();
            double dist   = haversineKm(lat, lng, segLat, segLng);
            if (dist < minDist) {
                minDist = dist;
                bestIdx = seg.getSegmentIndex();
            }
        }
        return bestIdx;
    }

    private double haversineKm(double lat1, double lon1,
                               double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private int driversAhead(String corridorCode, int currentSeg, int total) {
        int count = 0;
        for (int i = currentSeg + 1; i < Math.min(currentSeg + 4, total); i++) {
            String v = redisTemplate.opsForValue()
                    .get("demand:" + corridorCode + ":seg:" + i + ":drivers");
            if (v != null) count += Integer.parseInt(v);
        }
        return count;
    }
}