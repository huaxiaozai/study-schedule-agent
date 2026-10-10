package com.studyplanner.importing;

import com.studyplanner.contract.ImportProposal;

@FunctionalInterface
public interface ImportCommitPort {

    void commit(ImportProposal proposal);
}
