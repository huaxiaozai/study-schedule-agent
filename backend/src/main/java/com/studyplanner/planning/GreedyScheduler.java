package com.studyplanner.planning;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.studyplanner.contract.ScheduleSnapshot;
import com.studyplanner.contract.TaskSnapshot;
import com.studyplanner.contract.TaskStatus;
import com.studyplanner.contract.TimeBlock;
import com.studyplanner.contract.TimeBlockKind;

public final class GreedyScheduler {

    public static final Duration PLANNING_HORIZON = Duration.ofDays(14);
    public static final Duration SLOT_DURATION = Duration.ofMinutes(30);

    private static final long SLOT_SECONDS = SLOT_DURATION.toSeconds();
    private static final Comparator<TaskSnapshot> TASK_ORDER = Comparator
            .comparing(TaskSnapshot::deadline)
            .thenComparing(Comparator.comparingInt(TaskSnapshot::priority).reversed())
            .thenComparing(TaskSnapshot::id);

    private final HardConstraintValidator validator;
    private final Clock clock;

    public GreedyScheduler(HardConstraintValidator validator, Clock clock) {
        this.validator = Objects.requireNonNull(validator, "validator is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public ScheduleCandidate schedule(ScheduleSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot is required");

        Instant planningStart = alignUp(latest(snapshot.now(), clock.instant()));
        Instant horizonEnd = planningStart.plus(PLANNING_HORIZON);
        List<CandidateBlock> accepted = new ArrayList<>();
        List<PlanningGap> gaps = new ArrayList<>();

        List<TaskSnapshot> orderedTasks = snapshot.tasks().stream()
                .filter(GreedyScheduler::isSchedulable)
                .sorted(TASK_ORDER)
                .toList();
        Instant nextSearchStart = planningStart;
        for (TaskSnapshot task : orderedTasks) {
            nextSearchStart = scheduleTask(
                    snapshot,
                    task,
                    nextSearchStart,
                    horizonEnd,
                    accepted,
                    gaps);
        }

        return new ScheduleCandidate(accepted, gaps);
    }

    private Instant scheduleTask(
            ScheduleSnapshot snapshot,
            TaskSnapshot task,
            Instant searchStart,
            Instant horizonEnd,
            List<CandidateBlock> accepted,
            List<PlanningGap> gaps) {
        int remainingMinutes = remainingMinutes(task);
        if (remainingMinutes == 0) {
            return searchStart;
        }

        int requiredSlots = divideRoundingUp(remainingMinutes, Math.toIntExact(SLOT_DURATION.toMinutes()));
        Instant taskWindowEnd = task.deadline().isBefore(horizonEnd) ? task.deadline() : horizonEnd;
        List<CandidateBlock> provisional = new ArrayList<>(requiredSlots);

        Instant cursor = searchStart;
        while (provisional.size() < requiredSlots && !cursor.plus(SLOT_DURATION).isAfter(taskWindowEnd)) {
            CandidateBlock candidate = new CandidateBlock(task.id(), cursor, cursor.plus(SLOT_DURATION));
            List<CandidateBlock> occupiedCandidates = new ArrayList<>(accepted.size() + provisional.size());
            occupiedCandidates.addAll(accepted);
            occupiedCandidates.addAll(provisional);
            ScheduleSnapshot validationSnapshot = withCandidateBlocks(snapshot, occupiedCandidates);
            if (validator.validate(validationSnapshot, candidate).isEmpty()) {
                provisional.add(candidate);
            }
            cursor = cursor.plus(SLOT_DURATION);
        }

        if (provisional.size() == requiredSlots) {
            accepted.addAll(provisional);
            return cursor;
        }

        gaps.add(new PlanningGap(
                task.id(),
                remainingMinutes,
                Math.toIntExact(provisional.size() * SLOT_DURATION.toMinutes()),
                PlanningGap.INSUFFICIENT_CAPACITY_BEFORE_DEADLINE));
        return searchStart;
    }

    private static ScheduleSnapshot withCandidateBlocks(
            ScheduleSnapshot snapshot,
            List<CandidateBlock> candidates) {
        if (candidates.isEmpty()) {
            return snapshot;
        }

        List<TaskSnapshot> tasks = snapshot.tasks().stream()
                .map(task -> withTaskCandidates(task, candidates))
                .toList();
        return new ScheduleSnapshot(
                snapshot.schemaVersion(),
                snapshot.now(),
                snapshot.timezone(),
                snapshot.courses(),
                tasks,
                snapshot.lockedBlocks(),
                snapshot.activeFacts(),
                snapshot.planningRevision());
    }

    private static TaskSnapshot withTaskCandidates(TaskSnapshot task, List<CandidateBlock> candidates) {
        List<TimeBlock> additions = candidates.stream()
                .filter(candidate -> candidate.taskId().equals(task.id()))
                .map(candidate -> new TimeBlock(
                        candidateBlockId(candidate),
                        candidate.taskId(),
                        candidate.start(),
                        candidate.end(),
                        false,
                        TimeBlockKind.TASK))
                .toList();
        if (additions.isEmpty()) {
            return task;
        }

        List<TimeBlock> blocks = new ArrayList<>(task.scheduledBlocks().size() + additions.size());
        blocks.addAll(task.scheduledBlocks());
        blocks.addAll(additions);
        return new TaskSnapshot(
                task.id(),
                task.courseId(),
                task.title(),
                task.deadline(),
                task.estimatedDurationMinutes(),
                task.priority(),
                task.status(),
                blocks);
    }

    private static String candidateBlockId(CandidateBlock candidate) {
        return "planning-" + candidate.taskId() + "-" + candidate.start().getEpochSecond();
    }

    private static boolean isSchedulable(TaskSnapshot task) {
        return task.status() == TaskStatus.TODO || task.status() == TaskStatus.IN_PROGRESS;
    }

    private static int remainingMinutes(TaskSnapshot task) {
        long scheduledMinutes = task.scheduledBlocks().stream()
                .mapToLong(block -> Duration.between(block.start(), block.end()).toMinutes())
                .sum();
        long remaining = Math.max(0, task.estimatedDurationMinutes() - scheduledMinutes);
        return Math.toIntExact(remaining);
    }

    private static int divideRoundingUp(int dividend, int divisor) {
        return Math.floorDiv(dividend + divisor - 1, divisor);
    }

    private static Instant alignUp(Instant instant) {
        long epochSecond = instant.getEpochSecond();
        long remainder = Math.floorMod(epochSecond, SLOT_SECONDS);
        boolean alreadyAligned = remainder == 0 && instant.getNano() == 0;
        if (alreadyAligned) {
            return instant;
        }
        return Instant.ofEpochSecond(epochSecond - remainder + SLOT_SECONDS);
    }

    private static Instant latest(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }
}
