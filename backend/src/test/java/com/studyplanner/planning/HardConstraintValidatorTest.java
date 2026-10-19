package com.studyplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.IntNode;
import com.studyplanner.contract.CourseSnapshot;
import com.studyplanner.contract.ProfileFact;
import com.studyplanner.contract.ProfileFactState;
import com.studyplanner.contract.ScheduleSnapshot;
import com.studyplanner.contract.TaskSnapshot;
import com.studyplanner.contract.TaskStatus;
import com.studyplanner.contract.TimeBlock;
import com.studyplanner.contract.TimeBlockKind;

class HardConstraintValidatorTest {

    private final HardConstraintValidator validator = new HardConstraintValidator();

    @Test
    void rejectsCandidateEndingAfterDeadline() {
        ScheduleSnapshot snapshot = snapshot(
                task("task-1", "2026-09-14T10:00:00Z", List.of()),
                List.of(),
                List.of());

        List<ConstraintViolation> violations = validator.validate(
                snapshot,
                candidate("task-1", "2026-09-14T09:30:00Z", "2026-09-14T10:30:00Z"));

        assertThat(violations).extracting(ConstraintViolation::code)
                .containsExactly(ConstraintCode.DEADLINE_EXCEEDED);
    }

    @Test
    void acceptsCandidateEndingExactlyAtDeadline() {
        ScheduleSnapshot snapshot = snapshot(
                task("task-1", "2026-09-14T10:00:00Z", List.of()),
                List.of(),
                List.of());

        assertThat(validator.validate(
                snapshot,
                candidate("task-1", "2026-09-14T09:00:00Z", "2026-09-14T10:00:00Z")))
                .isEmpty();
    }

    @Test
    void rejectsOverlapWithExistingTaskBlock() {
        TimeBlock existing = block(
                "block-existing", "task-2", "2026-09-14T09:00:00Z", "2026-09-14T10:00:00Z", false,
                TimeBlockKind.TASK);
        ScheduleSnapshot snapshot = snapshot(
                task("task-1", "2026-09-15T10:00:00Z", List.of()),
                List.of(task("task-2", "2026-09-15T10:00:00Z", List.of(existing))),
                List.of());

        assertThat(validator.validate(
                snapshot,
                candidate("task-1", "2026-09-14T09:30:00Z", "2026-09-14T10:30:00Z")))
                .extracting(ConstraintViolation::code)
                .containsExactly(ConstraintCode.TIME_OVERLAP);
    }

    @Test
    void permitsTouchingIntervalBoundaries() {
        TimeBlock existing = block(
                "block-existing", "task-2", "2026-09-14T09:00:00Z", "2026-09-14T10:00:00Z", false,
                TimeBlockKind.TASK);
        ScheduleSnapshot snapshot = snapshot(
                task("task-1", "2026-09-15T10:00:00Z", List.of()),
                List.of(task("task-2", "2026-09-15T10:00:00Z", List.of(existing))),
                List.of());

        assertThat(validator.validate(
                snapshot,
                candidate("task-1", "2026-09-14T10:00:00Z", "2026-09-14T10:30:00Z")))
                .isEmpty();
    }

    @Test
    void reportsLockedBlockSeparatelyFromOrdinaryOverlap() {
        TimeBlock locked = block(
                "locked-1", "calendar", "2026-09-14T09:00:00Z", "2026-09-14T10:00:00Z", true,
                TimeBlockKind.PERSONAL);
        ScheduleSnapshot snapshot = snapshot(
                task("task-1", "2026-09-15T10:00:00Z", List.of()),
                List.of(),
                List.of(locked));

        assertThat(validator.validate(
                snapshot,
                candidate("task-1", "2026-09-14T09:30:00Z", "2026-09-14T10:30:00Z")))
                .extracting(ConstraintViolation::code)
                .containsExactly(ConstraintCode.LOCKED_BLOCK_OVERLAP);
    }

    @Test
    void reportsForbiddenPeriodWithHigherSpecificity() {
        TimeBlock forbidden = block(
                "forbidden-1", "profile", "2026-09-14T09:00:00Z", "2026-09-14T10:00:00Z", true,
                TimeBlockKind.UNAVAILABLE);
        ScheduleSnapshot snapshot = snapshot(
                task("task-1", "2026-09-15T10:00:00Z", List.of()),
                List.of(),
                List.of(forbidden));

        assertThat(validator.validate(
                snapshot,
                candidate("task-1", "2026-09-14T09:30:00Z", "2026-09-14T10:30:00Z")))
                .extracting(ConstraintViolation::code)
                .containsExactly(ConstraintCode.FORBIDDEN_PERIOD);
    }

    @Test
    void rejectsCandidateThatExceedsDailyStudyLimit() {
        TimeBlock existing = block(
                "study-1", "task-2", "2026-09-14T08:00:00Z", "2026-09-14T09:00:00Z", false,
                TimeBlockKind.TASK);
        ScheduleSnapshot snapshot = snapshot(
                task("task-1", "2026-09-15T10:00:00Z", List.of()),
                List.of(task("task-2", "2026-09-15T10:00:00Z", List.of(existing))),
                List.of(dailyLimit(90)));

        assertThat(validator.validate(
                snapshot,
                candidate("task-1", "2026-09-14T10:00:00Z", "2026-09-14T11:00:00Z")))
                .extracting(ConstraintViolation::code)
                .containsExactly(ConstraintCode.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    void appliesDailyLimitPerLocalDateAcrossMidnight() {
        TimeBlock secondDayStudy = block(
                "study-next-day", "task-2", "2026-09-14T16:30:00Z", "2026-09-14T17:30:00Z", false,
                TimeBlockKind.TASK);
        ScheduleSnapshot snapshot = snapshot(
                task("task-1", "2026-09-16T10:00:00Z", List.of()),
                List.of(task("task-2", "2026-09-16T10:00:00Z", List.of(secondDayStudy))),
                List.of(dailyLimit(60)));

        List<ConstraintViolation> violations = validator.validate(
                snapshot,
                candidate("task-1", "2026-09-14T15:30:00Z", "2026-09-14T16:30:00Z"));

        assertThat(violations).extracting(ConstraintViolation::code)
                .containsExactly(ConstraintCode.DAILY_LIMIT_EXCEEDED);
        assertThat(violations.getFirst().objectIds()).contains("2026-09-15");
    }

    @Test
    void returnsViolationsInStableCodeThenObjectOrder() {
        TimeBlock laterId = block(
                "locked-z", "calendar", "2026-09-14T09:00:00Z", "2026-09-14T11:00:00Z", true,
                TimeBlockKind.PERSONAL);
        TimeBlock earlierId = block(
                "locked-a", "calendar", "2026-09-14T09:00:00Z", "2026-09-14T11:00:00Z", true,
                TimeBlockKind.PERSONAL);
        ScheduleSnapshot snapshot = snapshot(
                task("task-1", "2026-09-14T10:00:00Z", List.of()),
                List.of(),
                List.of(laterId, earlierId));
        CandidateBlock candidate = candidate("task-1", "2026-09-14T09:30:00Z", "2026-09-14T10:30:00Z");

        List<ConstraintViolation> first = validator.validate(snapshot, candidate);
        List<ConstraintViolation> second = validator.validate(snapshot, candidate);

        assertThat(second).isEqualTo(first);
        assertThat(first).extracting(ConstraintViolation::code)
                .containsExactly(
                        ConstraintCode.DEADLINE_EXCEEDED,
                        ConstraintCode.LOCKED_BLOCK_OVERLAP,
                        ConstraintCode.LOCKED_BLOCK_OVERLAP);
        assertThat(first.subList(1, 3)).extracting(v -> v.objectIds().getLast())
                .containsExactly("locked-a", "locked-z");
    }

    private static ScheduleSnapshot snapshot(
            TaskSnapshot primaryTask,
            List<TaskSnapshot> otherTasks,
            List<?> factsOrBlocks) {
        List<TimeBlock> lockedBlocks = factsOrBlocks.stream()
                .filter(TimeBlock.class::isInstance)
                .map(TimeBlock.class::cast)
                .toList();
        List<ProfileFact> facts = factsOrBlocks.stream()
                .filter(ProfileFact.class::isInstance)
                .map(ProfileFact.class::cast)
                .toList();
        java.util.ArrayList<TaskSnapshot> tasks = new java.util.ArrayList<>();
        tasks.add(primaryTask);
        tasks.addAll(otherTasks);
        return new ScheduleSnapshot(
                1,
                Instant.parse("2026-09-13T01:00:00Z"),
                "Asia/Shanghai",
                List.<CourseSnapshot>of(),
                tasks,
                lockedBlocks,
                facts,
                1);
    }

    private static TaskSnapshot task(String id, String deadline, List<TimeBlock> blocks) {
        return new TaskSnapshot(
                id,
                "course-1",
                "Task " + id,
                Instant.parse(deadline),
                60,
                3,
                TaskStatus.TODO,
                blocks);
    }

    private static ProfileFact dailyLimit(int minutes) {
        return new ProfileFact(
                1,
                "planning.dailyLimitMinutes",
                IntNode.valueOf(minutes),
                "GLOBAL",
                1.0,
                "USER_EXPLICIT",
                ProfileFactState.ACTIVE,
                List.of("setting"),
                1);
    }

    private static TimeBlock block(
            String id,
            String ownerId,
            String start,
            String end,
            boolean locked,
            TimeBlockKind kind) {
        return new TimeBlock(id, ownerId, Instant.parse(start), Instant.parse(end), locked, kind);
    }

    private static CandidateBlock candidate(String taskId, String start, String end) {
        return new CandidateBlock(taskId, Instant.parse(start), Instant.parse(end));
    }
}
