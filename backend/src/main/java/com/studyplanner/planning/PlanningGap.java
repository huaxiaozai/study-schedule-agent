package com.studyplanner.planning;

public record PlanningGap(
        String taskId,
        int requiredMinutes,
        int availableMinutes,
        String reason) {

    public static final String INSUFFICIENT_CAPACITY_BEFORE_DEADLINE =
            "INSUFFICIENT_CAPACITY_BEFORE_DEADLINE";

    public PlanningGap {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        if (requiredMinutes <= 0) {
            throw new IllegalArgumentException("requiredMinutes must be positive");
        }
        if (availableMinutes < 0) {
            throw new IllegalArgumentException("availableMinutes must not be negative");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
    }
}
