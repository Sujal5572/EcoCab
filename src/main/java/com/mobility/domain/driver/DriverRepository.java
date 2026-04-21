package com.mobility.domain.driver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

// FIX: Was empty class. DriverService calls all of these.
public interface DriverRepository extends JpaRepository<Driver, UUID> {

    List<Driver> findAllByStatus(Driver.DriverStatus status);

    long countByStatus(Driver.DriverStatus status);

    @Query("SELECT d FROM Driver d WHERE d.currentCorridor.id = :corridorId " +
            "AND d.status IN ('ONLINE', 'ON_TRIP')")
    List<Driver> findOnlineDriversByCorridor(@Param("corridorId") UUID corridorId);

    @Modifying
    @Query(value = """
        UPDATE drivers
        SET last_known_location   = ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
            location_updated_at   = NOW(),
            current_segment_index = :segmentIndex,
            updated_at            = NOW()
        WHERE id = :driverId
        """, nativeQuery = true)
    void updateLocationAndSegment(@Param("driverId")     UUID driverId,
                                  @Param("lat")          double lat,
                                  @Param("lng")          double lng,
                                  @Param("segmentIndex") int segmentIndex);
}