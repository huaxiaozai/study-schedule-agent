package com.studyplanner.importing;

import java.time.Instant;
import java.util.List;

import com.studyplanner.contract.ContractError;
import com.studyplanner.contract.ImportProposal;
import com.studyplanner.contract.ImportStatus;

public record ImportJobView(
        String requestKey,
        ImportSourceType sourceType,
        String fileName,
        ImportStatus status,
        ImportProposal proposal,
        List<ContractError> errors,
        Instant createdAt,
        Instant updatedAt) {

    public static ImportJobView from(ImportJob job) {
        return new ImportJobView(
                job.requestKey(),
                job.sourceType(),
                job.fileName(),
                job.status(),
                job.proposal(),
                job.errors(),
                job.createdAt(),
                job.updatedAt());
    }
}
