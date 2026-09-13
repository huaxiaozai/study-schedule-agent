package com.studyplanner.contract;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ContractSupport {

    static final int CURRENT_SCHEMA_VERSION = 1;

    private ContractSupport() {
    }

    static void requireCurrentSchemaVersion(int schemaVersion) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported schemaVersion: " + schemaVersion + "; expected: " + CURRENT_SCHEMA_VERSION);
        }
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    static String requireZoneId(String value) {
        requireText(value, "timezone");
        try {
            return ZoneId.of(value).getId();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("timezone must be a valid IANA ZoneId: " + value, exception);
        }
    }

    static <T> List<T> immutableList(List<T> value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        return List.copyOf(value);
    }

    static <K, V> Map<K, V> immutableMap(Map<K, V> value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
