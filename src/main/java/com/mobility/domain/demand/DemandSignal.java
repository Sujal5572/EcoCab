package com.mobility.domain.demand;

import com.mobility.domain.corridor.Corridor;
import com.mobility.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "demand_signals")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class DemandSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corridor_id", nullable = false)
    private Corridor corridor;

    @Column(name = "segment_index", nullable = false)
    private Integer segmentIndex;

    @Column(name = "user_location", columnDefinition = "geography(Point,4326)")
    private Point userLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DemandStatus status = DemandStatus.ACTIVE;

    @Builder.Default
    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    public enum DemandStatus { ACTIVE, PICKED_UP, EXPIRED, CANCELLED }
}