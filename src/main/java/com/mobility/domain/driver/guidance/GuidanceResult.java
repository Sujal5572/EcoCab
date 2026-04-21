package com.mobility.domain.driver.guidance;
import lombok.*;

@Getter @Builder
public class GuidanceResult {
    private GuidanceAction action;
    private int            recommendedSegment;
    private String         reason;
    private Urgency        urgency;
    private int            demandAhead;
}