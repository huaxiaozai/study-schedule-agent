package com.studyplanner.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import com.studyplanner.contract.ImportProposal;
import com.studyplanner.contract.ImportStatus;
import com.studyplanner.contract.ContractError;

class ImportReviewRegressionTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void modelFailureCanBeRetriedWithTheSameRequestKey() {
        AtomicInteger calls = new AtomicInteger();
        ImportService service = new ImportService(source -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("temporary outage");
            return completeProposal();
        }, new InMemoryImportJobRepository(), new InMemoryImportCommitPort(), CLOCK);
        assertThat(service.submit(source()).status()).isEqualTo(ImportStatus.FAILED);
        assertThat(service.submit(source()).status()).isEqualTo(ImportStatus.REVIEW);
    }

    @Test
    void commitFailureCanBeRetriedWithoutReuploading() {
        AtomicInteger calls = new AtomicInteger();
        ImportService service = new ImportService(source -> completeProposal(),
                new InMemoryImportJobRepository(), proposal -> {
                    if (calls.incrementAndGet() == 1) throw new IllegalStateException("temporary outage");
                }, CLOCK);
        ImportProposal proposal = service.submit(source()).proposal();
        assertThat(service.confirm("review-test", proposal).status()).isEqualTo(ImportStatus.FAILED);
        assertThat(service.confirm("review-test", proposal).status()).isEqualTo(ImportStatus.COMMITTED);
    }

    @Test
    void unresolvedMockProposalCannotBeCommitted() {
        InMemoryImportCommitPort commits = new InMemoryImportCommitPort();
        ImportService service = new ImportService(new MockImportModelAdapter(),
                new InMemoryImportJobRepository(), commits, CLOCK);
        ImportProposal proposal = service.submit(source()).proposal();
        assertThatThrownBy(() -> service.confirm("review-test", proposal))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(commits.committedProposals()).isEmpty();
    }

    @Test
    void retryAfterLostCommitResponseWritesOnceAndClearsErrors() {
        InMemoryImportCommitPort commits = new InMemoryImportCommitPort();
        AtomicInteger calls = new AtomicInteger();
        ImportService service = new ImportService(source -> completeProposal(),
                new InMemoryImportJobRepository(), proposal -> {
                    commits.commit(proposal);
                    if (calls.incrementAndGet() == 1) throw new IllegalStateException("response lost");
                }, CLOCK);
        ImportProposal proposal = service.submit(source()).proposal();
        ImportJobView failed = service.confirm("review-test", proposal);
        assertThat(failed.errors()).extracting(ContractError::code).containsExactly(ImportService.COMMIT_FAILED);
        assertThat(service.submit(source())).isEqualTo(failed);
        ImportProposal changed = new ImportProposal(1, proposal.proposalId(), "ASSIGNMENT", "Changed", null,
                proposal.deadline(), 60, proposal.sourceEvidence(), List.of(), ImportStatus.REVIEW, List.of());
        assertThatThrownBy(() -> service.confirm("review-test", changed)).isInstanceOf(IllegalArgumentException.class);
        ImportJobView recovered = service.confirm("review-test", proposal);
        assertThat(recovered.status()).isEqualTo(ImportStatus.COMMITTED);
        assertThat(recovered.errors()).isEmpty();
        assertThat(service.confirm("review-test", proposal)).isEqualTo(recovered);
        assertThat(commits.committedProposals()).hasSize(1);
        assertThat(calls).hasValue(2);
    }

    @Test
    void commitPortRejectsChangedPayloadForTheSameProposalId() {
        InMemoryImportCommitPort commits = new InMemoryImportCommitPort();
        ImportProposal original = completeProposal();
        commits.commit(original);
        commits.commit(original);
        ImportProposal changed = new ImportProposal(1, original.proposalId(), "ASSIGNMENT", "Changed", null,
                original.deadline(), 60, original.sourceEvidence(), List.of(), ImportStatus.REVIEW, List.of());
        assertThatThrownBy(() -> commits.commit(changed)).isInstanceOf(IllegalArgumentException.class);
        assertThat(commits.committedProposals()).containsExactly(original);
    }

    @Test
    void missingTaskFieldsErrorsAndWrongStatusNeverReachCommitPort() {
        ImportProposal original = completeProposal();
        List<ImportProposal> invalid = List.of(
                new ImportProposal(1, original.proposalId(), "ASSIGNMENT", "Homework", null,
                        null, 60, List.of(), List.of(), ImportStatus.REVIEW, List.of()),
                new ImportProposal(1, original.proposalId(), "ASSIGNMENT", "Homework", null,
                        original.deadline(), null, List.of(), List.of(), ImportStatus.REVIEW, List.of()),
                new ImportProposal(1, original.proposalId(), "ASSIGNMENT", "Homework", null,
                        original.deadline(), 60, List.of(), List.of(), ImportStatus.REVIEW,
                        List.of(new ContractError("INVALID", "needs review", List.of()))),
                new ImportProposal(1, original.proposalId(), "ASSIGNMENT", "Homework", null,
                        original.deadline(), 60, List.of(), List.of(), ImportStatus.COMMITTED, List.of()));
        InMemoryImportCommitPort commits = new InMemoryImportCommitPort();
        ImportService service = new ImportService(source -> original,
                new InMemoryImportJobRepository(), commits, CLOCK);
        service.submit(source());
        for (ImportProposal proposal : invalid) {
            assertThatThrownBy(() -> service.confirm("review-test", proposal))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(service.get("review-test").status()).isEqualTo(ImportStatus.REVIEW);
        }
        assertThat(commits.committedProposals()).isEmpty();
    }

    private static ImportSource source() {
        return new ImportSource("review-test", ImportSourceType.SCREENSHOT, "sample.png", new byte[]{1});
    }

    private static ImportProposal completeProposal() {
        return new ImportProposal(1, "proposal-review-test", "ASSIGNMENT", "Homework", "Course",
                Instant.parse("2026-10-20T00:00:00Z"), 60, List.of("sample.png"), List.of(),
                ImportStatus.REVIEW, List.of());
    }
}
