package com.studyplanner.importing;

import java.util.Arrays;
import java.util.Objects;

public record ImportSource(
        String requestKey,
        ImportSourceType sourceType,
        String fileName,
        byte[] content) {

    private static final int MAX_SCREENSHOT_BYTES = 10 * 1024 * 1024;
    private static final int MAX_AUDIO_BYTES = 10 * 1024 * 1024;

    public ImportSource {
        requestKey = requireText(requestKey, "requestKey");
        Objects.requireNonNull(sourceType, "sourceType is required");
        fileName = requireText(fileName, "fileName");
        Objects.requireNonNull(content, "content is required");
        if (content.length == 0) {
            throw new IllegalArgumentException("content must not be empty");
        }
        if (sourceType == ImportSourceType.SCREENSHOT && content.length > MAX_SCREENSHOT_BYTES) {
            throw new IllegalArgumentException("screenshot must not exceed 10 MB");
        }
        if (sourceType == ImportSourceType.AUDIO && content.length > MAX_AUDIO_BYTES) {
            throw new IllegalArgumentException("audio must not exceed 10 MB");
        }
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public int contentLength() {
        return content.length;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ImportSource that)) {
            return false;
        }
        return requestKey.equals(that.requestKey)
                && sourceType == that.sourceType
                && fileName.equals(that.fileName)
                && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(requestKey, sourceType, fileName);
        return 31 * result + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "ImportSource[requestKey=" + requestKey
                + ", sourceType=" + sourceType
                + ", fileName=" + fileName
                + ", contentLength=" + content.length + "]";
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
