package com.studyplanner.planning;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record CandidateBlock(
        String taskId,
        Instant start,
        Instant end) {

    private static final long SLOT_SECONDS = Duration.ofMinutes(30).toSeconds();

    public CandidateBlock {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        Objects.requireNonNull(start, "start is required");
        Objects.requireNonNull(end, "end is required");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end");
        }
        if (start.getNano() != 0 || end.getNano() != 0
                || Math.floorMod(start.getEpochSecond(), SLOT_SECONDS) != 0
                || Math.floorMod(end.getEpochSecond(), SLOT_SECONDS) != 0) {
            throw new IllegalArgumentException("candidate boundaries must align to 30-minute slots");
        }
    }

    public long durationMinutes() {
        return Duration.between(start, end).toMinutes();
    }
}
