package com.studyplanner.importing;

import com.studyplanner.contract.ImportProposal;

@FunctionalInterface
public interface ImportCommitPort {

    /**
     * Commit idempotently by proposalId. Repeating an identical proposal must not
     * write twice, including when the first response was lost after writing.
     * Reject different content for an already committed proposalId. Production
     * adapters must enforce this atomically with their business transaction.
     */
    void commit(ImportProposal proposal);
}
