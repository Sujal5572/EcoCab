package com.mobility.domain.driver.guidance;

import lombok.*;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
class ScoredSegment {
    private int    index;
    private double rawRatio;
    private double weightedScore;
}