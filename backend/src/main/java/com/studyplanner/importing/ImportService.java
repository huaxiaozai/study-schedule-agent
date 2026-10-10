package com.studyplanner.importing;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.studyplanner.contract.ContractError;
import com.studyplanner.contract.ImportProposal;
import com.studyplanner.contract.ImportStatus;

public final class ImportService {

    public static final String MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE";
    public static final String COMMIT_FAILED = "COMMIT_FAILED";

    private final ImportModelPort modelPort;
    private final ImportJobRepository repository;
    private final ImportCommitPort commitPort;
    private final Clock clock;

    public ImportService(
            ImportModelPort modelPort,
            ImportJobRepository repository,
            ImportCommitPort commitPort,
            Clock clock) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort is required");
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.commitPort = Objects.requireNonNull(commitPort, "commitPort is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public synchronized ImportJobView submit(ImportSource source) {
        Objects.requireNonNull(source, "source is required");
        ImportJob existing = repository.findByRequestKey(source.requestKey()).orElse(null);
        if (existing != null) {
            return ImportJobView.from(existing);
        }

        ImportJob processing = repository.save(ImportJob.processing(source, clock.instant()));
        try {
            ImportProposal proposal = Objects.requireNonNull(
                    modelPort.propose(source),
                    "model proposal is required");
            ImportProposal reviewProposal = copyWithStatus(proposal, ImportStatus.REVIEW);
            ImportJob review = processing.withProposal(reviewProposal, ImportStatus.REVIEW, clock.instant());
            return ImportJobView.from(repository.save(review));
        } catch (RuntimeException exception) {
            ImportJob failed = processing.failed(
                    List.of(new ContractError(
                            MODEL_UNAVAILABLE,
                            "模型识别失败，请重试或手动填写",
                            List.of(source.requestKey()))),
                    clock.instant());
            return ImportJobView.from(repository.save(failed));
        }
    }

    public synchronized ImportJobView get(String requestKey) {
        return ImportJobView.from(findRequired(requestKey));
    }

    public synchronized ImportJobView confirm(String requestKey, ImportProposal confirmedProposal) {
        Objects.requireNonNull(confirmedProposal, "confirmedProposal is required");
        ImportJob current = findRequired(requestKey);
        if (current.status() == ImportStatus.COMMITTED) {
            return ImportJobView.from(current);
        }
        if (current.status() != ImportStatus.REVIEW) {
            throw new IllegalStateException("only a reviewed import can be committed");
        }
        if (current.proposal() != null
                && !current.proposal().proposalId().equals(confirmedProposal.proposalId())) {
            throw new IllegalArgumentException("proposalId does not match the reviewed import");
        }

        ImportProposal committedProposal = copyWithStatus(confirmedProposal, ImportStatus.COMMITTED);
        try {
            commitPort.commit(committedProposal);
        } catch (RuntimeException exception) {
            ImportJob failed = current.failed(
                    List.of(new ContractError(
                            COMMIT_FAILED,
                            "导入提交失败，请重试",
                            List.of(requestKey))),
                    clock.instant());
            return ImportJobView.from(repository.save(failed));
        }

        ImportJob committed = current.withProposal(committedProposal, ImportStatus.COMMITTED, clock.instant());
        return ImportJobView.from(repository.save(committed));
    }

    private ImportJob findRequired(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            throw new IllegalArgumentException("requestKey is required");
        }
        return repository.findByRequestKey(requestKey)
                .orElseThrow(() -> new ImportNotFoundException(requestKey));
    }

    private static ImportProposal copyWithStatus(ImportProposal proposal, ImportStatus status) {
        return new ImportProposal(
                proposal.schemaVersion(),
                proposal.proposalId(),
                proposal.kind(),
                proposal.title(),
                proposal.courseCandidate(),
                proposal.deadline(),
                proposal.durationMinutes(),
                proposal.sourceEvidence(),
                proposal.ambiguities(),
                status,
                proposal.errors());
    }
}
