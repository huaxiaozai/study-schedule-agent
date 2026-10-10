package com.studyplanner.importing;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.studyplanner.contract.ImportProposal;

/**
 * Temporary demo implementation until the business persistence module supplies
 * the transaction-backed import commit adapter.
 */
public final class InMemoryImportCommitPort implements ImportCommitPort {

    private final CopyOnWriteArrayList<ImportProposal> committed = new CopyOnWriteArrayList<>();

    @Override
    public void commit(ImportProposal proposal) {
        if (proposal == null) {
            throw new IllegalArgumentException("proposal is required");
        }
        committed.add(proposal);
    }

    public List<ImportProposal> committedProposals() {
        return List.copyOf(committed);
    }
}
