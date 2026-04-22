package com.mobility.domain.demand;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "demand_aggregates")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DemandAggregate {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "corridor_id", nullable = false)    private UUID    corridorId;
    @Column(name = "segment_index", nullable = false)  private Integer segmentIndex;
    @Column(name = "active_demand_count")              private Integer activeDemandCount;
    @Column(name = "drivers_in_segment")               private Integer driversInSegment;
    @Column(name = "window_start")                     private Instant windowStart;
    @Column(name = "window_end")                       private Instant windowEnd;

    @Builder.Default
    @Column(name = "computed_at")
    private Instant computedAt = Instant.now();
}