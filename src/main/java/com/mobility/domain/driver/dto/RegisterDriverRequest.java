package com.mobility.domain.driver.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RegisterDriverRequest {
    @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$")
    private String phoneNumber;
    @NotBlank private String name;
    @NotBlank private String vehicleNumber;
    @NotBlank private String vehicleType;
}