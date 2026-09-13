package com.studyplanner.contract;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

public record TimeBlock(
        String id,
        String ownerId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant start,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant end,
        boolean locked,
        TimeBlockKind kind) {

    public TimeBlock {
        id = ContractSupport.requireText(id, "id");
        ownerId = ContractSupport.requireText(ownerId, "ownerId");
        Objects.requireNonNull(start, "start is required");
        Objects.requireNonNull(end, "end is required");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
    }
}
