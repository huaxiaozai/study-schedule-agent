package com.studyplanner.importing;

import com.studyplanner.contract.ImportProposal;

@FunctionalInterface
public interface ImportModelPort {

    ImportProposal propose(ImportSource source);
}
