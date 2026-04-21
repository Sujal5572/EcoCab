package com.mobility.domain.driver.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

// domain/driver/dto/CorridorHeatmapResponse.java
@Getter
@Builder
public class CorridorHeatmapResponse {
    private UUID corridorId;
    private String            corridorCode;
    private int               totalSegments;
    private List<SegmentDensity> segments;

    @Getter @Builder
    public static class SegmentDensity {
        private int    segmentIndex;
        private int    demandCount;
        private int    driverCount;
        private double demandPerDriver;  // the key guidance metric
        private String densityLevel;     // "LOW" | "MEDIUM" | "HIGH" | "SURGE"
    }
}
