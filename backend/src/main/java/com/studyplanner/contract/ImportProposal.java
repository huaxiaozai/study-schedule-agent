package com.studyplanner.contract;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

public record ImportProposal(
        int schemaVersion,
        String proposalId,
        String kind,
        String title,
        String courseCandidate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant deadline,
        Integer durationMinutes,
        List<String> sourceEvidence,
        List<String> ambiguities,
        ImportStatus status,
        List<ContractError> errors) {

    public ImportProposal {
        ContractSupport.requireCurrentSchemaVersion(schemaVersion);
        proposalId = ContractSupport.requireText(proposalId, "proposalId");
        kind = ContractSupport.requireText(kind, "kind");
        title = ContractSupport.requireText(title, "title");
        if (durationMinutes != null && durationMinutes <= 0) {
            throw new IllegalArgumentException("durationMinutes must be positive when present");
        }
        sourceEvidence = ContractSupport.immutableList(sourceEvidence, "sourceEvidence");
        ambiguities = ContractSupport.immutableList(ambiguities, "ambiguities");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        errors = ContractSupport.immutableList(errors, "errors");
    }
}
