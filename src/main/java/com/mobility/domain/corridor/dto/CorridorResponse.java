package com.mobility.domain.corridor.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CorridorResponse {
    private UUID id;
    private String name;
    private String code;
    private int totalSegments;
}