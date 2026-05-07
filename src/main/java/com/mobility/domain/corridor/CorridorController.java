package com.mobility.domain.corridor;

import com.mobility.common.ApiResponse;
import com.mobility.domain.driver.dto.CorridorHeatmapResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/corridors")
@RequiredArgsConstructor
@Validated
public class CorridorController {

    private final CorridorService corridorService;

    @GetMapping
    public ApiResponse<List<Corridor>> listActiveCors() {
        return ApiResponse.ok(corridorService.listActive());
    }

    @GetMapping("/{id}")
    public ApiResponse<Corridor> getCorridor(@PathVariable UUID id) {
        return ApiResponse.ok(corridorService.getDetail(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Corridor> createCorridor(@RequestBody Corridor corridor) {
        return ApiResponse.ok("Corridor created", corridorService.create(corridor));
    }

    @GetMapping("/{id}/demand")
    public ApiResponse<CorridorHeatmapResponse> getLiveDemand(@PathVariable UUID id) {
        System.out.println("UUUID IS" +  id);
        return ApiResponse.ok(corridorService.getLiveDemand(id));
    }
}