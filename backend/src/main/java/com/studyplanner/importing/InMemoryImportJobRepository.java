package com.studyplanner.importing;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryImportJobRepository implements ImportJobRepository {

    private final ConcurrentMap<String, ImportJob> jobs = new ConcurrentHashMap<>();

    @Override
    public Optional<ImportJob> findByRequestKey(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(jobs.get(requestKey));
    }

    @Override
    public ImportJob save(ImportJob job) {
        if (job == null) {
            throw new IllegalArgumentException("job is required");
        }
        jobs.put(job.requestKey(), job);
        return job;
    }
}
