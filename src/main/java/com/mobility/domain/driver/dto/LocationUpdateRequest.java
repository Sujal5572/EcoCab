package com.mobility.domain.driver.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

// domain/driver/dto/LocationUpdateRequest.java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationUpdateRequest {

    @NotNull
    @DecimalMin("6.0") @DecimalMax("37.0")
    private Double latitude;

    @NotNull @DecimalMin("68.0") @DecimalMax("97.0")
    private Double longitude;

    // Optional: client-side speed in km/h for guidance quality
    private Double speedKmh;
    private Double headingDegrees;
}