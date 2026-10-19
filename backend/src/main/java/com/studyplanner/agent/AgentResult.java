package com.studyplanner.agent;

import com.studyplanner.contract.ActionSpec;
import com.studyplanner.contract.ActionStatus;
import com.studyplanner.planning.ScheduleCandidate;

public record AgentResult(
        ActionStatus status,
        ActionSpec action,
        ScheduleCandidate scheduleCandidate,
        String message) {

    public AgentResult {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (action != null && action.status() != status) {
            throw new IllegalArgumentException("result status must match action status");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
    }

    public static AgentResult clarification(String message) {
        return new AgentResult(ActionStatus.NEEDS_CLARIFICATION, null, null, message);
    }

    public static AgentResult failed(String message) {
        return new AgentResult(ActionStatus.FAILED, null, null, message);
    }
}
