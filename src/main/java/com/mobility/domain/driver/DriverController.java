package com.mobility.domain.driver;

import com.mobility.common.ApiResponse;
import com.mobility.common.BusinessException;
import com.mobility.domain.driver.dto.*;
import io.github.resilience4j.ratelimiter.RateLimiter;        // FIX: was Tomcat internal class
import io.github.resilience4j.ratelimiter.RateLimiterRegistry; // FIX: was missing
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor @Validated @Slf4j
public class DriverController {

    private final DriverService       driverService;
    private final RateLimiterRegistry rateLimiterRegistry;

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DriverResponse> register(
            @Valid @RequestBody RegisterDriverRequest request) {
        return ApiResponse.ok("Driver registered", driverService.register(request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<DriverResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody DriverStatusUpdateRequest request) {
        return ApiResponse.ok(driverService.updateStatus(id, request));
    }

    @PutMapping("/{id}/location")
    public ApiResponse<LocationUpdateResponse> updateLocation(
            @PathVariable UUID id,
            @Valid @RequestBody LocationUpdateRequest request) {
        // FIX: was org.apache.catalina.util.RateLimiter (Tomcat internal — completely wrong)
        RateLimiter limiter = rateLimiterRegistry.rateLimiter("driver-location-" + id);
        if (!limiter.acquirePermission()) {
            throw new BusinessException("Rate limit exceeded. Slow down pings.");
        }
        return ApiResponse.ok(driverService.updateLocation(id, request));
    }

    @GetMapping("/{id}/corridor-heatmap")
    public ApiResponse<CorridorHeatmapResponse> getCorridorHeatmap(@PathVariable UUID id) {
        return ApiResponse.ok(driverService.getCorridorHeatmap(id));
    }

    @PostMapping("/{id}/trips") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TripResponse> startTrip(@PathVariable UUID id) {
        return ApiResponse.ok("Trip started", driverService.startTrip(id));
    }

    @PatchMapping("/{id}/trips/{tripId}/pickup")
    public ApiResponse<TripResponse> recordPickup(
            @PathVariable UUID id, @PathVariable UUID tripId) {
        return ApiResponse.ok(driverService.recordPickup(id, tripId));
    }

    @PatchMapping("/{id}/trips/{tripId}/end")
    public ApiResponse<TripResponse> endTrip(
            @PathVariable UUID id, @PathVariable UUID tripId) {
        return ApiResponse.ok(driverService.endTrip(id, tripId));
    }
}