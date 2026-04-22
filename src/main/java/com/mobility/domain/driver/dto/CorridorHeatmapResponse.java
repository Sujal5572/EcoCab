package com.mobility.domain.driver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorridorHeatmapResponse {
    private UUID   corridorId;
    private String corridorCode;
    private int    totalSegments;
    private List<SegmentDensity> segments;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentDensity {
        private int    segmentIndex;
        private int    demandCount;
        private int    driverCount;
        private double demandPerDriver;
        private String densityLevel;
    }
}