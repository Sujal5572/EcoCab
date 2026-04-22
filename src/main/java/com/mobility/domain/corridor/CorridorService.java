package com.mobility.domain.corridor;

import com.mobility.domain.driver.dto.CorridorHeatmapResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CorridorService {

    private final CorridorRepository corridorRepository;

    public List<Corridor> listActive() {
        return corridorRepository.findAllActive();
    }

    public Corridor getDetail(UUID id) {
        return corridorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Corridor not found: " + id));
    }

    public Corridor create(Corridor corridor) {
        return corridorRepository.save(corridor);
    }

    // Live demand served from the heatmap cached in Redis
    // DriverService handles the Redis read — returning null triggers the caller to handle it
    public CorridorHeatmapResponse getLiveDemand(UUID id) {
        // Placeholder — real implementation reads from Redis heatmap key
        // Full implementation goes in Phase 4 DemandService wiring
        return null;
    }
}