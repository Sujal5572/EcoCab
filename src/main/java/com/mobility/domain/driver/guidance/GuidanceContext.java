package com.mobility.domain.driver.guidance;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter @Builder
public class GuidanceContext {
    private UUID   driverId;
    private UUID   corridorId;
    private String corridorCode;
    private int    currentSegmentIndex;
    private int    totalSegments;
    private int    maxDriverCapacity;
    private double speedKmh;
    private List<SegmentSnapshot> segments;
}