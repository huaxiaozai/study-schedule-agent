package com.studyplanner.importing;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import com.studyplanner.contract.ImportProposal;

/**
 * Temporary demo implementation until the business persistence module supplies
 * the transaction-backed import commit adapter.
 */
public final class InMemoryImportCommitPort implements ImportCommitPort {

    private final Map<String, ImportProposal> committed = new LinkedHashMap<>();

    @Override
    public synchronized void commit(ImportProposal proposal) {
        if (proposal == null) {
            throw new IllegalArgumentException("proposal is required");
        }
        ImportProposal existing = committed.get(proposal.proposalId());
        if (existing != null && !existing.equals(proposal)) {
            throw new IllegalArgumentException("proposalId already committed with different content");
        }
        committed.putIfAbsent(proposal.proposalId(), proposal);
    }

    public synchronized List<ImportProposal> committedProposals() {
        return List.copyOf(committed.values());
    }
}
