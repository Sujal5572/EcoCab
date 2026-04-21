package com.mobility.domain.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

// FIX: Was empty class
public interface TripRepository extends JpaRepository<Trip, UUID> {

    @Query("SELECT t FROM Trip t WHERE t.driver.id = :driverId " +
            "AND t.status = 'IN_PROGRESS'")
    Optional<Trip> findInProgressByDriver(@Param("driverId") UUID driverId);
}