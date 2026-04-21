package com.mobility.domain.demand;

import com.mobility.domain.corridor.Corridor;
import com.mobility.domain.user.User;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import org.locationtech.jts.geom.Point;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Getter;
import java.time.Instant;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

// domain/demand/DemandSignal.java
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

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    public enum DemandStatus { ACTIVE, PICKED_UP, EXPIRED, CANCELLED }
}
