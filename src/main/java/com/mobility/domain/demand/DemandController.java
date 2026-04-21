package com.mobility.domain.demand;

import com.mobility.common.ApiResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;
// domain/demand/DemandController.java
@RestController
@RequestMapping("/api/v1/demand")
@RequiredArgsConstructor
public class DemandController {

    private final DemandService demandService;

    /**
     * GET /api/v1/demand/corridors/{corridorId}/history
     * Time-series demand for a corridor — served from TimescaleDB.
     * Used by ops dashboard for trend analysis.
     */
    @GetMapping("/corridors/{corridorId}/history")
    public ApiResponse<List<DemandAggregateResponse>> getDemandHistory(
            @PathVariable UUID corridorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "SEGMENT") DemandGroupBy groupBy) {
        return ApiResponse.ok(demandService.getHistory(corridorId, from, to, groupBy));
    }

    /**
     * GET /api/v1/demand/corridors/{corridorId}/segments/{segmentIndex}
     * Live demand for a single segment — from Redis.
     */
    @GetMapping("/corridors/{corridorId}/segments/{segmentIndex}")
    public ApiResponse<SegmentDemandResponse> getSegmentDemand(
            @PathVariable UUID    corridorId,
            @PathVariable int     segmentIndex) {
        return ApiResponse.ok(demandService.getSegmentDemand(corridorId, segmentIndex));
    }
}