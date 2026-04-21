package com.mobility.domain.user.dto;

import com.mobility.domain.user.User;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.UUID;

// FIX: Was empty class. UserService calls UserResponse.from(user) which didn't exist.
@Getter
@Builder
public class UserResponse {

    private UUID    id;
    private String  phoneNumber;
    private String  name;
    private String  status;
    private Instant createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .phoneNumber(user.getPhoneNumber())
                .name(user.getName())
                .status(user.getStatus().name())
                .createdAt(user.getCreatedAt())
                .build();
    }
}