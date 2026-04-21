package com.mobility.infrastructure.kafka.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// infrastructure/kafka/events/DemandCreatedEvent.java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DemandCreatedEvent {
    private UUID signalId;
    private UUID   corridorId;
    private String corridorCode;   // denormalized — no DB lookup in consumer
    private int    segmentIndex;
    private UUID   userId;
    private Instant occurredAt = Instant.now();
}
