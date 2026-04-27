package com.mobility.domain.user;

import com.mobility.common.BusinessException;
import com.mobility.common.EntityNotFoundException;
import com.mobility.domain.corridor.Corridor;
import com.mobility.domain.corridor.CorridorRepository;
import com.mobility.domain.demand.DemandSignal;
import com.mobility.domain.demand.DemandSignalRepository;
import com.mobility.domain.user.dto.DemandSignalRequest;
import com.mobility.domain.user.dto.DemandSignalResponse;
import com.mobility.domain.user.dto.RegisterUserRequest;
import com.mobility.domain.user.dto.UserResponse;
import com.mobility.infrastructure.kafka.DemandEventProducer;
import com.mobility.infrastructure.kafka.events.DemandCancelledEvent;
import com.mobility.infrastructure.kafka.events.DemandCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository              userRepository;
    private final DemandSignalRepository      demandSignalRepository;
    private final CorridorRepository          corridorRepository;
    private final DemandEventProducer         demandEventProducer;
    private final RedisTemplate<String, String> redisTemplate;

    // FIX: was java.awt.Point (AWT/Swing!) — must be JTS for PostGIS
    private static final GeometryFactory GF =
            new GeometryFactory(new PrecisionModel(), 4326);

    public UserResponse register(RegisterUserRequest request) {
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new BusinessException("Phone number already registered");
        }
        User user = User.builder()
                .phoneNumber(request.getPhoneNumber())
                .name(request.getName())
                .status(User.UserStatus.ACTIVE)
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    public DemandSignalResponse createDemandSignal(UUID userId,
                                                   DemandSignalRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User", userId));

        Corridor corridor = corridorRepository.findById(request.getCorridorId())
                .orElseThrow(() -> new EntityNotFoundException("Corridor",
                        request.getCorridorId()));

        if (!corridor.getIsActive()) {
            throw new BusinessException("Corridor is currently inactive");
        }

        // One active signal per user at a time
        demandSignalRepository.cancelActiveSignalsForUser(userId);

        int segmentIndex = resolveSegmentIndex(corridor,
                request.getLatitude(), request.getLongitude());

        // FIX: was java.awt.Point — must be org.locationtech.jts.geom.Point
        Point userPoint = GF.createPoint(
                new Coordinate(request.getLongitude(), request.getLatitude()));

        DemandSignal signal = DemandSignal.builder()
                .user(user)
                .corridor(corridor)
                .segmentIndex(segmentIndex)
                .userLocation(userPoint)
                .status(DemandSignal.DemandStatus.ACTIVE)
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build();

        DemandSignal saved = demandSignalRepository.save(signal);

        demandEventProducer.publishDemandCreated(DemandCreatedEvent.builder()
                .signalId(saved.getId())
                .corridorId(corridor.getId())
                .corridorCode(corridor.getCode())
                .segmentIndex(segmentIndex)
                .userId(userId)
                .build());

        String redisKey = "demand:" + corridor.getCode()
                + ":seg:" + segmentIndex + ":count";
        String countStr = redisTemplate.opsForValue().get(redisKey);
        int currentDemandCount = countStr != null ? Integer.parseInt(countStr) : 0;

        return DemandSignalResponse.builder()
                .signalId(saved.getId())
                .corridorId(corridor.getId())
                .corridorCode(corridor.getCode())
                .segmentIndex(segmentIndex)
                .currentDemandCount(currentDemandCount)
                .expiresAt(saved.getExpiresAt())
                .build();
    }

    @Transactional
    public void cancelDemandSignal(UUID userId, UUID signalId) {
        DemandSignal signal = demandSignalRepository.findById(signalId)
                .orElseThrow(() -> new EntityNotFoundException("DemandSignal", signalId));

        if (!signal.getUser().getId().equals(userId)) {
            throw new BusinessException("Signal does not belong to this user");
        }
        if (signal.getStatus() != DemandSignal.DemandStatus.ACTIVE) {
            throw new BusinessException("Signal is not active");
        }

        signal.setStatus(DemandSignal.DemandStatus.CANCELLED);
        demandSignalRepository.save(signal);

        demandEventProducer.publishDemandCancelled(DemandCancelledEvent.builder()
                .signalId(signalId)
                .corridorId(signal.getCorridor().getId())
                .corridorCode(signal.getCorridor().getCode())
                .segmentIndex(signal.getSegmentIndex())
                .build());
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return UserResponse.from(userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User", id)));
    }

    private int resolveSegmentIndex(Corridor corridor, double lat, double lng) {
        if (corridor.getSegments() == null || corridor.getSegments().isEmpty()) return 0;
        double min = Double.MAX_VALUE;
        int best = 0;
        for (var seg : corridor.getSegments()) {
            if (seg.getSegmentStart() == null) continue;
            double d = Math.pow(lat - seg.getSegmentStart().getY(), 2)
                    + Math.pow(lng - seg.getSegmentStart().getX(), 2);
            if (d < min) { min = d; best = seg.getSegmentIndex(); }
        }
        return best;
    }
}