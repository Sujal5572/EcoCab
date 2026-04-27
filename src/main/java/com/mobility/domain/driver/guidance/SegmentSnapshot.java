package com.mobility.domain.driver.guidance;

import lombok.*;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class SegmentSnapshot {
    private int index;
    private int demandCount;
    private int driverCount;

    public double getRatio() {
        return driverCount == 0 ? demandCount : (double) demandCount / driverCount;
    }
}