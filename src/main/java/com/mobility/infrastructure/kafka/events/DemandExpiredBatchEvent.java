package com.mobility.infrastructure.kafka.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DemandExpiredBatchEvent {
    private String corridorCode;
    private int    segmentIndex;
    private int    expiredCount;
    @Builder.Default
    private Instant occurredAt = Instant.now();
}