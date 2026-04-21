package com.mobility.domain.demand;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// domain/demand/DemandSignalRepository.java
public interface DemandSignalRepository extends JpaRepository<DemandSignal, UUID> {

    @Modifying
    @Query(value = """
        WITH expired AS (
            UPDATE demand_signals
            SET    status = 'EXPIRED'
            WHERE  status = 'ACTIVE'
              AND  expires_at < :now
            RETURNING corridor_id, segment_index,
                      (SELECT code FROM corridors WHERE id = corridor_id) AS corridor_code
        )
        SELECT corridor_code      AS corridorCode,
               segment_index      AS segmentIndex,
               COUNT(*)           AS expiredCount
        FROM   expired
        GROUP  BY corridor_code, segment_index
        """,
            nativeQuery = true)
    List<ExpiredSignalSummary> expireAndSummarize(@Param("now") Instant now);

    @Modifying
    @Query("UPDATE DemandSignal d SET d.status = 'CANCELLED' " +
            "WHERE d.user.id = :userId AND d.status = 'ACTIVE'")
    void cancelActiveSignalsForUser(@Param("userId") UUID userId);
}

