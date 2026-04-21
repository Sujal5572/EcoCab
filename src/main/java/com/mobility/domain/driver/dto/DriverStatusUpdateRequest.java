package com.mobility.domain.driver.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DriverStatusUpdateRequest {
    @NotBlank private String status;   // ONLINE | OFFLINE
    private UUID corridorId;           // required when going ONLINE
}