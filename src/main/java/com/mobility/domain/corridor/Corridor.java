package com.mobility.domain.corridor;

import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity @Table(name = "corridors")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Corridor {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 200) private String name;
    @Column(unique = true, nullable = false, length = 50) private String code;

    // FIX: was java.awt.Point — must be JTS for PostGIS hibernate-spatial
    @Column(name = "start_point", columnDefinition = "geography(Point,4326)")
    private Point startPoint;
    @Column(name = "end_point", columnDefinition = "geography(Point,4326)")
    private Point endPoint;

    @Column(name = "total_segments", nullable = false) private Integer totalSegments;
    @Column(name = "is_active", nullable = false) private Boolean isActive = true;
    @CreatedDate @Column(name = "created_at", updatable = false) private Instant createdAt;

    @OneToMany(mappedBy = "corridor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("segmentIndex ASC")
    private List<CorridorSegment> segments = new ArrayList<>();
}