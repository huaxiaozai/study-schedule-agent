package com.studyplanner.contract;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

public record ScheduleSnapshot(
        int schemaVersion,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant now,
        String timezone,
        List<CourseSnapshot> courses,
        List<TaskSnapshot> tasks,
        List<TimeBlock> lockedBlocks,
        List<ProfileFact> activeFacts,
        long planningRevision) {

    public ScheduleSnapshot {
        ContractSupport.requireCurrentSchemaVersion(schemaVersion);
        Objects.requireNonNull(now, "now is required");
        timezone = ContractSupport.requireZoneId(timezone);
        courses = ContractSupport.immutableList(courses, "courses");
        tasks = ContractSupport.immutableList(tasks, "tasks");
        lockedBlocks = ContractSupport.immutableList(lockedBlocks, "lockedBlocks");
        activeFacts = ContractSupport.immutableList(activeFacts, "activeFacts");
        if (planningRevision < 0) {
            throw new IllegalArgumentException("planningRevision must not be negative");
        }
    }
}
