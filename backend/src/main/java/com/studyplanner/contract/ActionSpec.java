package com.studyplanner.contract;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public record ActionSpec(
        int schemaVersion,
        String operationKey,
        long baseRevision,
        ActionType type,
        List<String> targets,
        Map<String, JsonNode> changes,
        String authorizationScope,
        List<String> reasonFacts,
        ActionStatus status,
        List<ContractError> errors) {

    public ActionSpec {
        ContractSupport.requireCurrentSchemaVersion(schemaVersion);
        operationKey = ContractSupport.requireText(operationKey, "operationKey");
        if (baseRevision < 0) {
            throw new IllegalArgumentException("baseRevision must not be negative");
        }
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        targets = ContractSupport.immutableList(targets, "targets");
        changes = ContractSupport.immutableMap(changes, "changes");
        authorizationScope = ContractSupport.requireText(authorizationScope, "authorizationScope");
        reasonFacts = ContractSupport.immutableList(reasonFacts, "reasonFacts");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        errors = ContractSupport.immutableList(errors, "errors");
    }
}
