package com.studyplanner.importing;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.studyplanner.contract.ContractError;
import com.studyplanner.contract.ImportProposal;
import com.studyplanner.contract.ImportStatus;

public record ImportJob(
        String requestKey,
        ImportSourceType sourceType,
        String fileName,
        ImportStatus status,
        ImportProposal proposal,
        List<ContractError> errors,
        Instant createdAt,
        Instant updatedAt) {

    public ImportJob {
        requestKey = requireText(requestKey, "requestKey");
        Objects.requireNonNull(sourceType, "sourceType is required");
        fileName = requireText(fileName, "fileName");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(errors, "errors is required");
        errors = List.copyOf(errors);
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (status == ImportStatus.COMMITTED && proposal == null) {
            throw new IllegalArgumentException("proposal is required for a committed import");
        }
    }

    public static ImportJob processing(ImportSource source, Instant now) {
        Objects.requireNonNull(source, "source is required");
        return new ImportJob(
                source.requestKey(),
                source.sourceType(),
                source.fileName(),
                ImportStatus.PROCESSING,
                null,
                List.of(),
                now,
                now);
    }

    public ImportJob withProposal(ImportProposal newProposal, ImportStatus newStatus, Instant newUpdatedAt) {
        return new ImportJob(
                requestKey,
                sourceType,
                fileName,
                newStatus,
                newProposal,
                errors,
                createdAt,
                newUpdatedAt);
    }

    public ImportJob failed(List<ContractError> newErrors, Instant newUpdatedAt) {
        return new ImportJob(
                requestKey,
                sourceType,
                fileName,
                ImportStatus.FAILED,
                proposal,
                newErrors,
                createdAt,
                newUpdatedAt);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
