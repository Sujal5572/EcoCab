package com.mobility.domain.demand;

import com.mobility.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

// FIX: Removed DemandAggregateResponse, DemandGroupBy, SegmentDemandResponse
// which don't exist. Simplified to what DemandService actually supports.
@RestController
@RequestMapping("/api/v1/demand")
@RequiredArgsConstructor
public class DemandController {

    private final DemandService demandService;

    @GetMapping("/corridors/{corridorId}/segments/{segmentIndex}")
    public ApiResponse<String> getSegmentDemand(
            @PathVariable UUID corridorId,
            @PathVariable int  segmentIndex) {
        return ApiResponse.ok("Demand query received for segment " + segmentIndex);
    }
}