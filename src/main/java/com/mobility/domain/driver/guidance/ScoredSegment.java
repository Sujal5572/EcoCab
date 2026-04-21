package com.mobility.domain.driver.guidance;
import lombok.*;

@Getter @Builder
class ScoredSegment {
    private int    index;
    private double rawRatio;
    private double weightedScore;
}