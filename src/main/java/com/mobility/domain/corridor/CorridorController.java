package com.mobility.domain.corridor;

import com.mobility.common.ApiResponse;

import com.mobility.domain.driver.dto.CorridorHeatmapResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;
// domain/corridor/CorridorController.java
@RestController
@RequestMapping("/api/v1/corridors")
@RequiredArgsConstructor
@Validated
public class CorridorController {

    private final CorridorService corridorService;

    /**
     * GET /api/v1/corridors
     * List all active corridors. Used by both passenger and driver app on startup.
     */
    @GetMapping
    public ApiResponse<List<CorridorResponse>> listActiveCors() {
        return ApiResponse.ok(corridorService.listActive());
    }

    /**
     * GET /api/v1/corridors/{id}
     * Full corridor detail including all segment coordinates.
     */
    @GetMapping("/{id}")
    public ApiResponse<CorridorDetailResponse> getCorridor(@PathVariable UUID id) {
        return ApiResponse.ok(corridorService.getDetail(id));
    }

    /**
     * POST /api/v1/corridors  [ADMIN ONLY]
     * Ops team defines a new corridor with segment breakdown.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CorridorResponse> createCorridor(
            @Valid @RequestBody CreateCorridorRequest request) {
        return ApiResponse.ok("Corridor created", corridorService.create(request));
    }

    /**
     * GET /api/v1/corridors/{id}/demand
     * Live demand snapshot for a corridor — all segments.
     * Served from Redis. Useful for ops dashboard.
     */
    @GetMapping("/{id}/demand")
    public ApiResponse<CorridorHeatmapResponse> getLiveDemand(
            @PathVariable UUID id) {
        return ApiResponse.ok(corridorService.getLiveDemand(id));
    }
}
