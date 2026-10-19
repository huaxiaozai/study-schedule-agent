package com.studyplanner.contract;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public record ProfileFact(
        int schemaVersion,
        String key,
        JsonNode value,
        String scope,
        double strength,
        String source,
        ProfileFactState state,
        List<String> evidenceIds,
        long version) {

    public ProfileFact {
        ContractSupport.requireCurrentSchemaVersion(schemaVersion);
        key = ContractSupport.requireText(key, "key");
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("value is required");
        }
        scope = ContractSupport.requireText(scope, "scope");
        if (!Double.isFinite(strength) || strength < 0.0 || strength > 1.0) {
            throw new IllegalArgumentException("strength must be between 0.0 and 1.0");
        }
        source = ContractSupport.requireText(source, "source");
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        evidenceIds = ContractSupport.immutableList(evidenceIds, "evidenceIds");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }
}
