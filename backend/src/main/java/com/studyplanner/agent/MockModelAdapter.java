package com.studyplanner.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.studyplanner.contract.ScheduleSnapshot;

public final class MockModelAdapter implements ModelAdapter {

    private final Map<String, ModelResponse> responses;

    public MockModelAdapter(Map<String, ModelResponse> responses) {
        Objects.requireNonNull(responses, "responses is required");
        this.responses = Map.copyOf(new LinkedHashMap<>(responses));
    }

    @Override
    public ModelResponse generate(String instruction, ScheduleSnapshot snapshot) {
        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("instruction is required");
        }
        Objects.requireNonNull(snapshot, "snapshot is required");
        ModelResponse response = responses.get(instruction);
        if (response != null) {
            return response;
        }
        return ModelResponse.clarification(null, java.util.List.of("instruction"));
    }
}
