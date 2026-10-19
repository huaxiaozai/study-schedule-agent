package com.studyplanner.importing;

public final class ImportNotFoundException extends RuntimeException {

    public ImportNotFoundException(String requestKey) {
        super("import job not found: " + requestKey);
    }
}
