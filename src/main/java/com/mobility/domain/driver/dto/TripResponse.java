package com.mobility.domain.driver.dto;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Builder
public class TripResponse {
    private UUID id; private UUID driverId;
    private UUID corridorId; private String status;
    private int passengersPickedUp;
    private Instant startedAt; private Instant endedAt;
}