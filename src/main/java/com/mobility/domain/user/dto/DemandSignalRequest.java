package com.mobility.domain.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

// domain/user/dto/DemandSignalRequest.java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandSignalRequest {

    @NotNull(message = "Corridor ID is required")
    private UUID corridorId;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "6.0",  message = "Latitude out of India bounds")
    @DecimalMax(value = "37.0", message = "Latitude out of India bounds")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "68.0",  message = "Longitude out of India bounds")
    @DecimalMax(value = "97.0",  message = "Longitude out of India bounds")
    private Double longitude;
}