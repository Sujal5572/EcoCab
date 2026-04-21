package com.mobility.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

// domain/user/dto/DemandSignalResponse.java
@Getter
@Builder
public class DemandSignalResponse {
    private UUID signalId;
    private UUID   corridorId;
    private String corridorCode;
    private int    segmentIndex;
    private int    currentDemandCount; // from Redis — what passenger sees
    private Instant expiresAt;
}