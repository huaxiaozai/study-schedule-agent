package com.studyplanner.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.studyplanner.contract.CourseSnapshot;
import com.studyplanner.contract.ScheduleSnapshot;
import com.studyplanner.contract.TaskSnapshot;
import com.studyplanner.contract.TaskStatus;
import com.studyplanner.contract.TimeBlock;
import com.studyplanner.contract.TimeBlockKind;

class GreedySchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-13T01:10:00Z");

    private final GreedyScheduler scheduler = new GreedyScheduler(
            new HardConstraintValidator(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void ordersTasksByDeadlineThenPriorityDescendingThenId() {
        ScheduleSnapshot snapshot = snapshot(
                List.of(),
                List.of(
                        task("task-z", "2026-09-14T12:00:00Z", 30, 5, TaskStatus.TODO, List.of()),
                        task("task-low", "2026-09-14T12:00:00Z", 30, 2, TaskStatus.TODO, List.of()),
                        task("task-b", "2026-09-14T12:00:00Z", 30, 5, TaskStatus.TODO, List.of()),
                        task("task-urgent", "2026-09-13T12:00:00Z", 30, 1, TaskStatus.TODO, List.of())),
                List.of());

        ScheduleCandidate result = scheduler.schedule(snapshot);

        assertThat(result.blocks()).extracting(CandidateBlock::taskId)
                .containsExactly("task-urgent", "task-b", "task-z", "task-low");
        assertThat(result.blocks()).extracting(CandidateBlock::start)
                .containsExactly(
                        Instant.parse("2026-09-13T01:30:00Z"),
                        Instant.parse("2026-09-13T02:00:00Z"),
                        Instant.parse("2026-09-13T02:30:00Z"),
                        Instant.parse("2026-09-13T03:00:00Z"));
        assertThat(result.gaps()).isEmpty();
    }

    @Test
    void skipsCourseExistingTaskLockedAndUnavailableBlocks() {
        TimeBlock courseBlock = block(
                "course-block", "course-1", "2026-09-13T01:30:00Z", "2026-09-13T02:00:00Z", true,
                TimeBlockKind.COURSE);
        TimeBlock existingTaskBlock = block(
                "existing-task", "other-task", "2026-09-13T02:00:00Z", "2026-09-13T02:30:00Z", false,
                TimeBlockKind.TASK);
        TimeBlock lockedBlock = block(
                "locked", "calendar", "2026-09-13T02:30:00Z", "2026-09-13T03:00:00Z", true,
                TimeBlockKind.PERSONAL);
        TimeBlock unavailableBlock = block(
                "unavailable", "profile", "2026-09-13T03:00:00Z", "2026-09-13T03:30:00Z", true,
                TimeBlockKind.UNAVAILABLE);
        ScheduleSnapshot snapshot = new ScheduleSnapshot(
                1,
                NOW,
                "Asia/Shanghai",
                List.of(new CourseSnapshot("course-1", "Course", List.of(courseBlock))),
                List.of(
                        task("target", "2026-09-14T12:00:00Z", 30, 3, TaskStatus.TODO, List.of()),
                        task("other-task", "2026-09-14T12:00:00Z", 30, 3, TaskStatus.IN_PROGRESS,
                                List.of(existingTaskBlock))),
                List.of(lockedBlock, unavailableBlock),
                List.of(),
                7);

        ScheduleCandidate result = scheduler.schedule(snapshot);

        assertThat(result.blocks()).filteredOn(block -> block.taskId().equals("target"))
                .extracting(CandidateBlock::start, CandidateBlock::end)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        Instant.parse("2026-09-13T03:30:00Z"),
                        Instant.parse("2026-09-13T04:00:00Z")));
    }

    @Test
    void subtractsExistingDurationAndUsesThirtyMinuteSlots() {
        TimeBlock completedPart = block(
                "started", "target", "2026-09-13T00:30:00Z", "2026-09-13T01:30:00Z", false,
                TimeBlockKind.TASK);
        ScheduleSnapshot snapshot = snapshot(
                List.of(),
                List.of(task("target", "2026-09-14T12:00:00Z", 95, 3, TaskStatus.IN_PROGRESS,
                        List.of(completedPart))),
                List.of());

        ScheduleCandidate result = scheduler.schedule(snapshot);

        assertThat(result.blocks()).hasSize(2);
        assertThat(result.blocks()).allSatisfy(block ->
                assertThat(block.durationMinutes()).isEqualTo(30));
        assertThat(result.gaps()).isEmpty();
    }

    @Test
    void returnsGapAndNoPartialBlocksWhenTaskCannotFitBeforeDeadline() {
        TimeBlock occupied = block(
                "occupied", "calendar", "2026-09-13T01:30:00Z", "2026-09-13T02:00:00Z", true,
                TimeBlockKind.PERSONAL);
        ScheduleSnapshot snapshot = snapshot(
                List.of(),
                List.of(task("target", "2026-09-13T02:30:00Z", 90, 3, TaskStatus.TODO, List.of())),
                List.of(occupied));

        ScheduleCandidate result = scheduler.schedule(snapshot);

        assertThat(result.blocks()).isEmpty();
        assertThat(result.gaps()).containsExactly(new PlanningGap(
                "target",
                90,
                30,
                PlanningGap.INSUFFICIENT_CAPACITY_BEFORE_DEADLINE));
    }

    @Test
    void skipsCompletedAndCancelledTasks() {
        ScheduleSnapshot snapshot = snapshot(
                List.of(),
                List.of(
                        task("done", "2026-09-14T12:00:00Z", 30, 5, TaskStatus.DONE, List.of()),
                        task("cancelled", "2026-09-14T12:00:00Z", 30, 5, TaskStatus.CANCELLED, List.of()),
                        task("todo", "2026-09-14T12:00:00Z", 30, 1, TaskStatus.TODO, List.of())),
                List.of());

        assertThat(scheduler.schedule(snapshot).blocks()).extracting(CandidateBlock::taskId)
                .containsExactly("todo");
    }

    @Test
    void schedulesOneHundredTasksDeterministicallyAcrossThirtyRuns() {
        List<TaskSnapshot> tasks = new ArrayList<>();
        for (int index = 99; index >= 0; index--) {
            tasks.add(task(
                    "task-%03d".formatted(index),
                    "2026-09-27T01:30:00Z",
                    30,
                    3,
                    TaskStatus.TODO,
                    List.of()));
        }
        ScheduleSnapshot snapshot = snapshot(List.of(), tasks, List.of());

        long startedAt = System.nanoTime();
        ScheduleCandidate expected = scheduler.schedule(snapshot);
        for (int run = 1; run < 30; run++) {
            assertThat(scheduler.schedule(snapshot)).isEqualTo(expected);
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(expected.blocks()).hasSize(100);
        assertThat(expected.gaps()).isEmpty();
        assertThat(expected.blocks().getFirst().taskId()).isEqualTo("task-000");
        assertThat(elapsed).isLessThan(Duration.ofSeconds(10));
    }

    private static ScheduleSnapshot snapshot(
            List<CourseSnapshot> courses,
            List<TaskSnapshot> tasks,
            List<TimeBlock> lockedBlocks) {
        return new ScheduleSnapshot(
                1,
                NOW,
                "Asia/Shanghai",
                courses,
                tasks,
                lockedBlocks,
                List.of(),
                7);
    }

    private static TaskSnapshot task(
            String id,
            String deadline,
            int durationMinutes,
            int priority,
            TaskStatus status,
            List<TimeBlock> scheduledBlocks) {
        return new TaskSnapshot(
                id,
                "course-1",
                "Task " + id,
                Instant.parse(deadline),
                durationMinutes,
                priority,
                status,
                scheduledBlocks);
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
}
