package com.mobility.domain.driver.dto;


import lombok.Builder;
import lombok.Getter;

// domain/driver/dto/LocationUpdateResponse.java
@Getter
@Builder
public class LocationUpdateResponse {
    private int    currentSegmentIndex;
    private int    demandAhead;        // total demand in segments ahead
    private int    driversAhead;       // competing drivers ahead
    private String guidance;           // "CONTINUE" | "SLOW_DOWN" | "SPEED_UP"
    private int    recommendedSegment; // where driver should head next
    private String guidanceReason; // FIX: was missing
    private String urgency;        // FIX: was missing
}


