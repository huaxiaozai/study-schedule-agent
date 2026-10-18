package com.studyplanner.importing;

import java.util.Optional;

public interface ImportJobRepository {

    Optional<ImportJob> findByRequestKey(String requestKey);

    ImportJob save(ImportJob job);
}
