package com.studyplanner.contract;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

public record TaskSnapshot(
        String id,
        String courseId,
        String title,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant deadline,
        int estimatedDurationMinutes,
        int priority,
        TaskStatus status,
        List<TimeBlock> scheduledBlocks) {

    public TaskSnapshot {
        id = ContractSupport.requireText(id, "id");
        title = ContractSupport.requireText(title, "title");
        Objects.requireNonNull(deadline, "deadline is required");
        if (estimatedDurationMinutes <= 0) {
            throw new IllegalArgumentException("estimatedDurationMinutes must be positive");
        }
        if (priority < 1 || priority > 5) {
            throw new IllegalArgumentException("priority must be between 1 and 5");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        scheduledBlocks = ContractSupport.immutableList(scheduledBlocks, "scheduledBlocks");
    }
}
