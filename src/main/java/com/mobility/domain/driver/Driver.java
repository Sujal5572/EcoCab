package com.mobility.domain.driver;

import com.mobility.domain.corridor.Corridor;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.UUID;

// FIX: Was entirely empty class. Full JPA entity added.
@Entity
@Table(name = "drivers")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Driver {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "phone_number", unique = true, nullable = false, length = 15)
    private String phoneNumber;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "vehicle_number", unique = true, nullable = false, length = 20)
    private String vehicleNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType = VehicleType.AUTO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DriverStatus status = DriverStatus.OFFLINE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_corridor_id")
    private Corridor currentCorridor;

    @Column(name = "current_segment_index")
    private Integer currentSegmentIndex;

    @Column(name = "last_known_location", columnDefinition = "geography(Point,4326)")
    private Point lastKnownLocation;

    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;

    @CreatedDate @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate @Column(name = "updated_at")
    private Instant updatedAt;

    public enum DriverStatus { OFFLINE, ONLINE, ON_TRIP, SUSPENDED }
    public enum VehicleType  { AUTO, MINI_BUS, E_RICKSHAW }
}