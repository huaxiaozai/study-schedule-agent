package com.studyplanner.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.studyplanner.contract.ContractError;
import com.studyplanner.contract.ImportProposal;
import com.studyplanner.contract.ImportStatus;

class ImportServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-10T10:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    @Test
    void submitIsIdempotentForTheSameRequestKey() {
        AtomicInteger modelCalls = new AtomicInteger();
        ImportService service = serviceWith(source -> {
            modelCalls.incrementAndGet();
            return proposal("proposal-" + source.requestKey(), ImportStatus.REVIEW);
        });

        ImportSource source = new ImportSource("request-1", ImportSourceType.SCREENSHOT, "capture.png",
                new byte[]{1, 2, 3});
        ImportJobView first = service.submit(source);
        ImportJobView second = service.submit(source);

        assertThat(modelCalls).hasValue(1);
        assertThat(first.status()).isEqualTo(ImportStatus.REVIEW);
        assertThat(second.proposal()).isEqualTo(first.proposal());
    }

    @Test
    void modelFailureReturnsStableFailureCodeInsteadOfThrowing() {
        ImportService service = serviceWith(source -> {
            throw new IllegalStateException("model unavailable");
        });

        ImportJobView failed = service.submit(new ImportSource(
                "request-2",
                ImportSourceType.AUDIO,
                "voice.m4a",
                new byte[]{1}));

        assertThat(failed.status()).isEqualTo(ImportStatus.FAILED);
        assertThat(failed.errors()).extracting(ContractError::code)
                .containsExactly(ImportService.MODEL_UNAVAILABLE);
    }

    @Test
    void confirmedImportCommitsOnceAndRepeatedConfirmationIsIdempotent() {
        InMemoryImportCommitPort commitPort = new InMemoryImportCommitPort();
        ImportService service = new ImportService(
                source -> proposal("proposal-confirm", ImportStatus.REVIEW),
                new InMemoryImportJobRepository(),
                commitPort,
                CLOCK);

        ImportJobView review = service.submit(new ImportSource(
                "request-3",
                ImportSourceType.SCREENSHOT,
                "capture.png",
                new byte[]{1}));
        ImportJobView committed = service.confirm("request-3", review.proposal());
        ImportJobView repeated = service.confirm("request-3", review.proposal());

        assertThat(committed.status()).isEqualTo(ImportStatus.COMMITTED);
        assertThat(repeated.status()).isEqualTo(ImportStatus.COMMITTED);
        assertThat(commitPort.committedProposals()).hasSize(1);
        assertThat(commitPort.committedProposals().getFirst().status()).isEqualTo(ImportStatus.COMMITTED);
    }

    private static ImportService serviceWith(ImportModelPort modelPort) {
        return new ImportService(
                modelPort,
                new InMemoryImportJobRepository(),
                new InMemoryImportCommitPort(),
                CLOCK);
    }

    private static ImportProposal proposal(String proposalId, ImportStatus status) {
        return new ImportProposal(
                1,
                proposalId,
                "ASSIGNMENT",
                "数据结构作业",
                "数据结构",
                Instant.parse("2026-10-12T12:00:00Z"),
                120,
                List.of("capture-001#line-3"),
                List.of(),
                status,
                List.of());
    }
}
