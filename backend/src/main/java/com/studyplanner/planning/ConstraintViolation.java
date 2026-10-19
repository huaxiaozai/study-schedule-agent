package com.studyplanner.planning;

import java.util.List;

public record ConstraintViolation(
        ConstraintCode code,
        List<String> objectIds,
        String message) {

    public ConstraintViolation {
        if (code == null) {
            throw new IllegalArgumentException("code is required");
        }
        if (objectIds == null) {
            throw new IllegalArgumentException("objectIds is required");
        }
        objectIds = List.copyOf(objectIds);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
    }
}
