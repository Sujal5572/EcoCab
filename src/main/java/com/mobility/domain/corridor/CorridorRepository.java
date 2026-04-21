package com.mobility.domain.corridor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

// FIX: Was empty class. DemandAggregationScheduler calls findAllActive().
public interface CorridorRepository extends JpaRepository<Corridor, UUID> {

    @Query("SELECT c FROM Corridor c WHERE c.isActive = true")
    List<Corridor> findAllActive();
}