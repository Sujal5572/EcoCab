package com.mobility.domain.trip;

import com.mobility.domain.corridor.Corridor;
import com.mobility.domain.driver.Driver;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

// FIX: Was entirely empty class
@Entity @Table(name = "trips")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Trip {

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false) private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corridor_id", nullable = false) private Corridor corridor;

    @Column(name = "start_segment_index") private Integer startSegmentIndex = 0;
    @Column(name = "end_segment_index")   private Integer endSegmentIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status = TripStatus.IN_PROGRESS;

    @Column(name = "passengers_picked_up") private Integer passengersPickedUp = 0;
    @Column(name = "started_at") private Instant startedAt = Instant.now();
    @Column(name = "ended_at")   private Instant endedAt;

    public enum TripStatus { IN_PROGRESS, COMPLETED, ABANDONED }
}