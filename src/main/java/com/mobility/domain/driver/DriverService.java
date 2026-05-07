package com.mobility.domain.driver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobility.common.EntityNotFoundException;
import com.mobility.domain.corridor.Corridor;
import com.mobility.domain.corridor.CorridorRepository;
import com.mobility.domain.corridor.CorridorSegment;
import com.mobility.domain.corridor.CorridorService;
import com.mobility.domain.driver.dto.*;
import com.mobility.domain.driver.guidance.*;
import com.mobility.domain.trip.Trip;
import com.mobility.domain.trip.TripRepository;
import jakarta.persistence.QueryTimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverService {

    private final DriverRepository              driverRepository;
    private final CorridorRepository            corridorRepository;
    private final TripRepository                tripRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final GuidanceEngine                guidanceEngine;
    private final ObjectMapper objectMapper;
    private final CorridorService corridorService;

    private static final Duration DRIVER_LOC_TTL    = Duration.ofSeconds(90);
    private static final Duration SEGMENT_SCORE_TTL  = Duration.ofSeconds(90);
    private static final int      DEFAULT_MAX_DRIVERS = 5;

    // ── register ─────────────────────────────────────────────────
    public DriverResponse register(RegisterDriverRequest req) {
        Driver driver = Driver.builder()
                .phoneNumber(req.getPhoneNumber())
                .name(req.getName())
                .vehicleNumber(req.getVehicleNumber())
                .vehicleType(Driver.VehicleType.valueOf(req.getVehicleType()))
                .status(Driver.DriverStatus.OFFLINE)
                .build();
        return DriverResponse.from(driverRepository.save(driver));
    }

    // ── updateStatus ──────────────────────────────────────────────
    public DriverResponse updateStatus(UUID id, DriverStatusUpdateRequest req) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Driver", id));
        driver.setStatus(Driver.DriverStatus.valueOf(req.getStatus()));
        if (req.getCorridorId() != null && "ONLINE".equals(req.getStatus())) {
            Corridor corridor = corridorRepository.findById(req.getCorridorId())
                    .orElseThrow(() -> new EntityNotFoundException("Corridor", req.getCorridorId()));
            driver.setCurrentCorridor(corridor);
        }
        return DriverResponse.from(driverRepository.save(driver));
    }

    // ── updateLocation (hot path) ────────────────────────────────
    @Transactional
    public LocationUpdateResponse updateLocation(UUID driverId, LocationUpdateRequest req) {
        try {
            return doUpdateLocation(driverId, req);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.error("Redis unavailable for driver={}, degrading", driverId, e);
            return degradedLocationUpdate(driverId, req);
        }
    }

    private LocationUpdateResponse doUpdateLocation(UUID driverId, LocationUpdateRequest req) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver", driverId));

        Corridor corridor = driver.getCurrentCorridor();
        if (corridor == null) {
            return degradedLocationUpdate(driverId, req);
        }

        int newSegment = resolveSegmentIndex(corridor, req.getLatitude(), req.getLongitude());
        int oldSegment = driver.getCurrentSegmentIndex() != null
                ? driver.getCurrentSegmentIndex() : newSegment;

        updateDriverInRedis(driver, corridor, oldSegment, newSegment,
                req.getLatitude(), req.getLongitude());

        driverRepository.updateLocationAndSegment(
                driverId, req.getLatitude(), req.getLongitude(), newSegment);

        GuidanceContext context = buildGuidanceContext(driver, corridor, newSegment, req);
        GuidanceResult  guidance = guidanceEngine.compute(context);

        log.debug("Driver {} seg {}→{} action={} urgency={}",
                driverId, oldSegment, newSegment, guidance.getAction(), guidance.getUrgency());

        return LocationUpdateResponse.builder()
                .currentSegmentIndex(newSegment)
                .demandAhead(guidance.getDemandAhead())
                .driversAhead(driversAhead(corridor.getCode(), newSegment,
                        corridor.getTotalSegments()))
                .guidance(guidance.getAction().name())
                .recommendedSegment(guidance.getRecommendedSegment())
                .guidanceReason(guidance.getReason())
                .urgency(guidance.getUrgency().name())
                .build();
    }

    private LocationUpdateResponse degradedLocationUpdate(UUID driverId, LocationUpdateRequest req) {
        try {
            driverRepository.updateLocationAndSegment(driverId,
                    req.getLatitude(), req.getLongitude(), 0);
        } catch (Exception e) {
            log.error("DB write also failed for driver={}", driverId, e);
        }
        return LocationUpdateResponse.builder()
                .currentSegmentIndex(0).demandAhead(0).driversAhead(0)
                .guidance("CONTINUE").recommendedSegment(1)
                .guidanceReason("Guidance temporarily unavailable.")
                .urgency("NONE").build();
    }

    // ── getCorridorHeatmap ────────────────────────────────────────
    @Transactional(readOnly = true)
    public CorridorHeatmapResponse getCorridorHeatmap(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver", driverId));

        Corridor corridor = driver.getCurrentCorridor();

        if (corridor == null) {
            return CorridorHeatmapResponse.builder()
                    .corridorId(null)
                    .corridorCode("NONE")
                    .corridorName("No Corridor") // ✅ ADD
                    .totalSegments(0)
                    .segments(List.of())
                    .build();
        }

        String cacheKey = "heatmap:" + corridor.getCode();
        String json = redisTemplate.opsForValue().get(cacheKey);

        // ✅ 1. Try Redis first
        if (json != null) {
            try {
                CorridorHeatmapResponse response =
                        objectMapper.readValue(json, CorridorHeatmapResponse.class);

                // ✅ IMPORTANT: enrich response (Redis won't have name)
                return CorridorHeatmapResponse.builder()
                        .corridorId(response.getCorridorId())
                        .corridorCode(response.getCorridorCode())
                        .corridorName(corridor.getName()) // ⭐ ADD HERE
                        .totalSegments(response.getTotalSegments())
                        .segments(response.getSegments())
                        .build();

            } catch (Exception e) {
                log.warn("Invalid heatmap JSON for {}", corridor.getCode());
            }
        }

        // ✅ 2. Fallback → build live
        CorridorHeatmapResponse response =
                corridorService.getLiveDemand(corridor.getId());

        // ✅ ALSO enrich fallback
        return CorridorHeatmapResponse.builder()
                .corridorId(response.getCorridorId())
                .corridorCode(response.getCorridorCode())
                .corridorName(corridor.getName())
                .totalSegments(response.getTotalSegments())
                .segments(response.getSegments())
                .build();
    }
    // ── startTrip ─────────────────────────────────────────────────
    public TripResponse startTrip(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver", driverId));
        Trip trip = Trip.builder()
                .driver(driver)
                .corridor(driver.getCurrentCorridor())
                .status(Trip.TripStatus.IN_PROGRESS)
                .build();
        Trip saved = tripRepository.save(trip);
        driver.setStatus(Driver.DriverStatus.ON_TRIP);
        driverRepository.save(driver);
        return toTripResponse(saved);
    }

    // ── recordPickup ──────────────────────────────────────────────
    public TripResponse recordPickup(UUID driverId, UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));
        trip.setPassengersPickedUp(trip.getPassengersPickedUp() + 1);
        return toTripResponse(tripRepository.save(trip));
    }

    // ── endTrip ───────────────────────────────────────────────────
    public TripResponse endTrip(UUID driverId, UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));
        trip.setStatus(Trip.TripStatus.COMPLETED);
        trip.setEndedAt(java.time.Instant.now());
        Trip saved = tripRepository.save(trip);
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver", driverId));
        driver.setStatus(Driver.DriverStatus.ONLINE);
        driverRepository.save(driver);
        return toTripResponse(saved);
    }

    // ── helpers ───────────────────────────────────────────────────
    private TripResponse toTripResponse(Trip t) {
        return TripResponse.builder()
                .id(t.getId())
                .driverId(t.getDriver().getId())
                .corridorId(t.getCorridor() != null ? t.getCorridor().getId() : null)
                .status(t.getStatus().name())
                .passengersPickedUp(t.getPassengersPickedUp())
                .startedAt(t.getStartedAt())
                .endedAt(t.getEndedAt())
                .build();
    }

    private void updateDriverInRedis(Driver driver, Corridor corridor,
                                     int oldSegment, int newSegment,
                                     double lat, double lng) {
        String code = corridor.getCode();
        redisTemplate.opsForValue().set(
                "driver:" + driver.getId() + ":location",
                lat + "," + lng + "," + newSegment,
                DRIVER_LOC_TTL);
        if (oldSegment != newSegment) {
            redisTemplate.execute(SWAP_DRIVER_SEGMENT_SCRIPT,
                    Arrays.asList(
                            "demand:" + code + ":seg:" + oldSegment + ":drivers",
                            "demand:" + code + ":seg:" + newSegment + ":drivers"));
        } else {
            String key = "demand:" + code + ":seg:" + newSegment + ":drivers";
            redisTemplate.opsForValue().setIfAbsent(key, "0");
            redisTemplate.expire(key, SEGMENT_SCORE_TTL);
        }
    }

    private static final RedisScript<Void> SWAP_DRIVER_SEGMENT_SCRIPT =
            RedisScript.of(
                    "local old = redis.call('GET', KEYS[1]) " +
                            "if old and tonumber(old) > 0 then redis.call('DECR', KEYS[1]) end " +
                            "redis.call('INCR', KEYS[2]) return nil",
                    Void.class);

    private GuidanceContext buildGuidanceContext(Driver driver, Corridor corridor,
                                                 int currentSegment, LocationUpdateRequest req) {
        String code      = corridor.getCode();
        int    totalSegs = corridor.getTotalSegments();
        List<Object> results = redisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    for (int i = 0; i < totalSegs; i++) {
                        connection.stringCommands()
                                .get(("demand:" + code + ":seg:" + i + ":count").getBytes());
                        connection.stringCommands()
                                .get(("demand:" + code + ":seg:" + i + ":drivers").getBytes());
                    }
                    return null;
                });
        List<SegmentSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < totalSegs; i++) {
            String c = (String) results.get(i * 2);
            String d = (String) results.get(i * 2 + 1);
            snapshots.add(SegmentSnapshot.builder()
                    .index(i)
                    .demandCount(c != null ? Integer.parseInt(c) : 0)
                    .driverCount(d != null ? Integer.parseInt(d) : 0)
                    .build());
        }
        return GuidanceContext.builder()
                .driverId(driver.getId()).corridorId(corridor.getId())
                .corridorCode(code).currentSegmentIndex(currentSegment)
                .totalSegments(totalSegs).maxDriverCapacity(DEFAULT_MAX_DRIVERS)
                .speedKmh(req.getSpeedKmh() != null ? req.getSpeedKmh() : 0.0)
                .segments(snapshots).build();
    }

    public int resolveSegmentIndex(Corridor corridor, double lat, double lng) {
        List<CorridorSegment> segments = corridor.getSegments();
        if (segments == null || segments.isEmpty()) return 0;
        double min = Double.MAX_VALUE; int best = 0;
        for (CorridorSegment seg : segments) {
            if (seg.getSegmentStart() == null) continue;
            double d = haversineKm(lat, lng,
                    seg.getSegmentStart().getY(), seg.getSegmentStart().getX());
            if (d < min) { min = d; best = seg.getSegmentIndex(); }
        }
        return best;
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2)*Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }

    private int driversAhead(String code, int seg, int total) {
        int count = 0;
        for (int i = seg+1; i < Math.min(seg+4, total); i++) {
            String v = redisTemplate.opsForValue().get("demand:"+code+":seg:"+i+":drivers");
            if (v != null) count += Integer.parseInt(v);
        }
        return count;
    }
}