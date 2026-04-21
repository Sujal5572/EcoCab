package com.mobility.infrastructure.kafka.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// infrastructure/kafka/events/DemandCancelledEvent.java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DemandCancelledEvent {
    private UUID signalId;
    private UUID   corridorId;
    private String corridorCode;
    private int    segmentIndex;
    private Instant occurredAt = Instant.now();
}