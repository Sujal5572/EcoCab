package com.mobility.domain.user;


import com.mobility.common.ApiResponse;
import com.mobility.domain.user.dto.DemandSignalRequest;
import com.mobility.domain.user.dto.DemandSignalResponse;
import com.mobility.domain.user.dto.RegisterUserRequest;
import com.mobility.domain.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import java.util.UUID;
// domain/user/UserController.java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * POST /api/v1/users/register
     * Passenger self-registration via OTP-verified phone number.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> register(
            @Valid @RequestBody RegisterUserRequest request) {
        UserResponse user = userService.register(request);
        return ApiResponse.ok("User registered successfully", user);
    }

    /**
     * GET /api/v1/users/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUser(@PathVariable UUID id) {
        return ApiResponse.ok(userService.findById(id));
    }

    /**
     * POST /api/v1/users/{id}/demand
     * Passenger signals intent to board on a corridor.
     * This is the core demand-creation API.
     * No driver is assigned — signal is aggregated into corridor heatmap.
     */
    @PostMapping("/{id}/demand")
    @ResponseStatus(HttpStatus.ACCEPTED)  // 202 — processed async via Kafka
    public ApiResponse<DemandSignalResponse> createDemandSignal(
            @PathVariable UUID id,
            @Valid @RequestBody DemandSignalRequest request) {
        DemandSignalResponse response = userService.createDemandSignal(id, request);
        return ApiResponse.ok("Demand signal registered", response);
    }

    /**
     * DELETE /api/v1/users/{id}/demand/{signalId}
     * Passenger cancels their demand (e.g. they found another ride).
     */
    @DeleteMapping("/{id}/demand/{signalId}")
    public ApiResponse<Void> cancelDemand(
            @PathVariable UUID id,
            @PathVariable UUID signalId) {
        userService.cancelDemandSignal(id, signalId);
        return ApiResponse.ok("Demand cancelled", null);
    }
}
