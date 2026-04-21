package com.mobility.infrastructure.kafka.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// infrastructure/kafka/events/DemandExpiredBatchEvent.java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DemandExpiredBatchEvent {
    private String corridorCode;
    private int    segmentIndex;
    private int    expiredCount;   // how many signals expired in this batch
    private Instant occurredAt = Instant.now();
}
