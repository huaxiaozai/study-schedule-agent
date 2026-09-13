package com.studyplanner.planning;

public enum ConstraintCode {
    TASK_NOT_FOUND(0),
    DEADLINE_EXCEEDED(10),
    FORBIDDEN_PERIOD(20),
    LOCKED_BLOCK_OVERLAP(30),
    TIME_OVERLAP(40),
    DAILY_LIMIT_EXCEEDED(50);

    private final int sortOrder;

    ConstraintCode(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    int sortOrder() {
        return sortOrder;
    }
}
