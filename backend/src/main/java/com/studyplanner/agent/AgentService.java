package com.studyplanner.agent;

import java.util.Objects;

import com.studyplanner.contract.ScheduleSnapshot;

public final class AgentService {

    private final ModelAdapter modelAdapter;
    private final AgentToolRegistry toolRegistry;

    public AgentService(ModelAdapter modelAdapter, AgentToolRegistry toolRegistry) {
        this.modelAdapter = Objects.requireNonNull(modelAdapter, "modelAdapter is required");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry is required");
    }

    public AgentResult handle(String operationKey, String instruction, ScheduleSnapshot snapshot) {
        requireText(operationKey, "operationKey");
        requireText(instruction, "instruction");
        Objects.requireNonNull(snapshot, "snapshot is required");

        ModelAdapter.ModelResponse response;
        try {
            response = Objects.requireNonNull(
                    modelAdapter.generate(instruction, snapshot),
                    "model response is required");
        } catch (RuntimeException exception) {
            return AgentResult.failed("MODEL_ADAPTER_FAILED");
        }

        if (!response.ambiguities().isEmpty()) {
            return AgentResult.clarification(
                    "需要补充信息: " + String.join(", ", response.ambiguities()));
        }

        try {
            return toolRegistry.execute(operationKey, snapshot, response);
        } catch (RuntimeException exception) {
            return AgentResult.failed("TOOL_EXECUTION_FAILED");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
