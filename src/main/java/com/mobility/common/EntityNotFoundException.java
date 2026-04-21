package com.mobility.common;

import java.util.UUID;

// Custom exceptions — never leak stack traces to client
public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String entity, UUID id) {
        super(entity + " not found with id: " + id);
    }
}
