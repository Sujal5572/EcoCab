package com.mobility.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class ApiError {
    private final String message;
    private Map<String, String> fieldErrors; // for validation failures

    public ApiError(String message) {
        this.message = message;
    }
}