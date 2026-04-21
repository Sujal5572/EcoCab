package com.mobility.domain.driver.dto;
import com.mobility.domain.driver.Driver;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Builder
public class DriverResponse {
    private UUID id; private String phoneNumber;
    private String name; private String vehicleNumber;
    private String vehicleType; private String status;
    private UUID currentCorridorId; private Integer currentSegmentIndex;
    private Instant createdAt;

    public static DriverResponse from(Driver d) {
        return DriverResponse.builder()
                .id(d.getId()).phoneNumber(d.getPhoneNumber()).name(d.getName())
                .vehicleNumber(d.getVehicleNumber()).vehicleType(d.getVehicleType().name())
                .status(d.getStatus().name())
                .currentCorridorId(d.getCurrentCorridor() != null ? d.getCurrentCorridor().getId() : null)
                .currentSegmentIndex(d.getCurrentSegmentIndex()).createdAt(d.getCreatedAt())
                .build();
    }
}