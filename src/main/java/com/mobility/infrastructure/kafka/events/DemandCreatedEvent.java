package com.mobility.infrastructure.kafka.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DemandCreatedEvent {
    private UUID   signalId;
    private UUID   corridorId;
    private String corridorCode;
    private int    segmentIndex;
    private UUID   userId;
    @Builder.Default
    private Instant occurredAt = Instant.now();
}