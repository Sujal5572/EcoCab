package com.mobility.domain.corridor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;
import java.util.UUID;

// FIX: Was entirely empty class
@Entity @Table(name = "corridor_segments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CorridorSegment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corridor_id", nullable = false)
    @JsonIgnore
    private Corridor corridor;

    @Column(name = "segment_index", nullable = false) private Integer segmentIndex;

    @Column(name = "segment_start", columnDefinition = "geography(Point,4326)")
    @JsonIgnore
    private Point segmentStart;
    @Column(name = "segment_end", columnDefinition = "geography(Point,4326)")
    @JsonIgnore
    private Point segmentEnd;

    @Column(name = "length_km", columnDefinition = "numeric(6,3)")
    private Double lengthKm;
    @Column(name = "max_driver_capacity") private Integer maxDriverCapacity = 5;
}