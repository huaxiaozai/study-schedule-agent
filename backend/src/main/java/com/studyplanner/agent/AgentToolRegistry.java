package com.studyplanner.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.studyplanner.contract.ActionSpec;
import com.studyplanner.contract.ActionStatus;
import com.studyplanner.contract.ActionType;
import com.studyplanner.contract.ContractError;
import com.studyplanner.contract.ScheduleSnapshot;
import com.studyplanner.planning.GreedyScheduler;
import com.studyplanner.planning.ScheduleCandidate;

public class AgentToolRegistry {

    private static final Set<ActionType> REGISTERED_TOOLS =
            Collections.unmodifiableSet(EnumSet.allOf(ActionType.class));

    private final GreedyScheduler scheduler;

    public AgentToolRegistry(GreedyScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler is required");
    }

    public Set<ActionType> registeredTools() {
        return REGISTERED_TOOLS;
    }

    public AgentResult execute(
            String operationKey,
            ScheduleSnapshot snapshot,
            ModelAdapter.ModelResponse response) {
        requireText(operationKey, "operationKey");
        Objects.requireNonNull(snapshot, "snapshot is required");
        Objects.requireNonNull(response, "response is required");
        if (response.type() == null || !REGISTERED_TOOLS.contains(response.type())) {
            throw new IllegalArgumentException("model selected an unregistered tool");
        }

        List<String> missingFields = missingFields(response);
        if (!missingFields.isEmpty()) {
            ContractError error = new ContractError(
                    "MISSING_ACTION_FIELDS",
                    "动作缺少必填字段",
                    missingFields);
            ActionSpec action = action(
                    operationKey,
                    snapshot,
                    response,
                    ActionStatus.NEEDS_CLARIFICATION,
                    List.of(error));
            return new AgentResult(
                    ActionStatus.NEEDS_CLARIFICATION,
                    action,
                    null,
                    "缺少动作字段: " + String.join(", ", missingFields));
        }

        ActionStatus status = isReadOnly(response.type())
                ? ActionStatus.APPLIED
                : ActionStatus.NEEDS_CONFIRMATION;
        ActionSpec action = action(operationKey, snapshot, response, status, List.of());
        ScheduleCandidate candidate = response.type() == ActionType.GENERATE_SCHEDULE
                ? scheduler.schedule(snapshot)
                : null;
        String message = status == ActionStatus.APPLIED ? "工具执行完成" : "动作等待用户确认";
        return new AgentResult(status, action, candidate, message);
    }

    private static ActionSpec action(
            String operationKey,
            ScheduleSnapshot snapshot,
            ModelAdapter.ModelResponse response,
            ActionStatus status,
            List<ContractError> errors) {
        return new ActionSpec(
                snapshot.schemaVersion(),
                operationKey,
                snapshot.planningRevision(),
                response.type(),
                response.targets(),
                response.changes(),
                authorizationScope(response.type()),
                response.reasonFacts(),
                status,
                errors);
    }

    private static List<String> missingFields(ModelAdapter.ModelResponse response) {
        List<String> missing = new ArrayList<>();
        Map<String, ?> changes = response.changes();
        switch (response.type()) {
            case CREATE_TASK -> {
                addIfMissing(changes, "title", missing);
                addIfMissing(changes, "deadline", missing);
                addIfMissing(changes, "durationMinutes", missing);
            }
            case MOVE_TASK -> {
                if (response.targets().size() != 1) {
                    missing.add("targets");
                }
                addIfMissing(changes, "start", missing);
                addIfMissing(changes, "end", missing);
            }
            case CANCEL_TASK -> {
                if (response.targets().size() != 1) {
                    missing.add("targets");
                }
            }
            case QUERY_SCHEDULE, GENERATE_SCHEDULE -> {
                // These tools only consume the schedule snapshot.
            }
        }
        return List.copyOf(missing);
    }

    private static void addIfMissing(Map<String, ?> changes, String field, List<String> missing) {
        if (!changes.containsKey(field) || changes.get(field) == null || changes.get(field).toString().isBlank()) {
            missing.add(field);
        }
    }

    private static boolean isReadOnly(ActionType type) {
        return type == ActionType.QUERY_SCHEDULE || type == ActionType.GENERATE_SCHEDULE;
    }

    private static String authorizationScope(ActionType type) {
        return switch (type) {
            case QUERY_SCHEDULE -> "schedule:read:self";
            case GENERATE_SCHEDULE -> "schedule:plan:self";
            case CREATE_TASK, MOVE_TASK, CANCEL_TASK -> "schedule:write:self";
        };
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
