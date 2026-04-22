package com.mobility.domain.demand;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

// FIX: This repository was referenced in DemandAggregationScheduler but never created
public interface DemandAggregateRepository extends JpaRepository<DemandAggregate, UUID> {
}