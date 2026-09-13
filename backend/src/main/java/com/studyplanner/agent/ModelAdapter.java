package com.studyplanner.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.studyplanner.contract.ActionType;
import com.studyplanner.contract.ScheduleSnapshot;

@FunctionalInterface
public interface ModelAdapter {

    ModelResponse generate(String instruction, ScheduleSnapshot snapshot);

    record ModelResponse(
            ActionType type,
            List<String> targets,
            Map<String, JsonNode> changes,
            List<String> reasonFacts,
            List<String> ambiguities) {

        public ModelResponse {
            if (targets == null) {
                throw new IllegalArgumentException("targets is required");
            }
            if (changes == null) {
                throw new IllegalArgumentException("changes is required");
            }
            if (reasonFacts == null) {
                throw new IllegalArgumentException("reasonFacts is required");
            }
            if (ambiguities == null) {
                throw new IllegalArgumentException("ambiguities is required");
            }
            targets = List.copyOf(targets);
            changes = Collections.unmodifiableMap(new LinkedHashMap<>(changes));
            reasonFacts = List.copyOf(reasonFacts);
            ambiguities = List.copyOf(ambiguities);
            if (type == null && ambiguities.isEmpty()) {
                throw new IllegalArgumentException("type is required when the response is not a clarification");
            }
        }

        public static ModelResponse clarification(ActionType intendedType, List<String> ambiguities) {
            return new ModelResponse(intendedType, List.of(), Map.of(), List.of(), ambiguities);
        }
    }
}
