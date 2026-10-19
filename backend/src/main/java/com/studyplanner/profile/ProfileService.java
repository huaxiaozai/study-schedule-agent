package com.studyplanner.profile;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studyplanner.contract.ProfileFact;
import com.studyplanner.contract.ProfileFactState;

public final class ProfileService {

    public static final String DURATION_SAMPLE_KEY_PREFIX = "duration.sample.";

    private static final String DATE_SCOPE_PREFIX = "DATE:";

    private final ProfileFactPort port;
    private final DurationEstimator durationEstimator;
    private final Clock clock;

    public ProfileService(ProfileFactPort port, DurationEstimator durationEstimator, Clock clock) {
        this.port = Objects.requireNonNull(port, "port is required");
        this.durationEstimator = Objects.requireNonNull(durationEstimator, "durationEstimator is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public ProfileFact setExplicitPreference(
            String key,
            JsonNode value,
            String scope,
            List<String> evidenceIds) {
        return append(key, value, scope, 1.0, "USER_EXPLICIT", ProfileFactState.ACTIVE, evidenceIds);
    }

    public ProfileFact setTemporaryException(
            String key,
            JsonNode value,
            LocalDate date,
            List<String> evidenceIds) {
        Objects.requireNonNull(date, "date is required");
        return append(
                key,
                value,
                DATE_SCOPE_PREFIX + date,
                1.0,
                "USER_EXPLICIT",
                ProfileFactState.ACTIVE,
                evidenceIds);
    }

    public ProfileFact denyFact(String key, String scope, List<String> evidenceIds) {
        return changeState(key, scope, ProfileFactState.DENIED, evidenceIds);
    }

    public ProfileFact deleteFact(String key, String scope, List<String> evidenceIds) {
        return changeState(key, scope, ProfileFactState.DELETED, evidenceIds);
    }

    public ProfileFact recordDurationSample(
            String scope,
            int estimatedMinutes,
            int actualMinutes,
            String evidenceId) {
        requireText(scope, "scope");
        DurationEstimator.DurationSample sample =
                new DurationEstimator.DurationSample(estimatedMinutes, actualMinutes, evidenceId);
        ObjectNode value = JsonNodeFactory.instance.objectNode()
                .put("estimatedMinutes", sample.estimatedMinutes())
                .put("actualMinutes", sample.actualMinutes())
                .put("evidenceId", sample.evidenceId());
        return append(
                DURATION_SAMPLE_KEY_PREFIX + sample.evidenceId(),
                value,
                scope,
                1.0,
                "TASK_COMPLETION",
                ProfileFactState.ACTIVE,
                List.of(sample.evidenceId()));
    }

    public DurationEstimator.DurationEstimate estimateDuration(
            String scope,
            int originalMinutes,
            boolean remainingManuallyEdited) {
        requireText(scope, "scope");
        List<DurationEstimator.DurationSample> samples = latestFacts().values().stream()
                .filter(fact -> fact.state() == ProfileFactState.ACTIVE)
                .filter(fact -> fact.scope().equals(scope))
                .filter(fact -> fact.key().startsWith(DURATION_SAMPLE_KEY_PREFIX))
                .map(ProfileService::toDurationSample)
                .sorted(Comparator.comparing(DurationEstimator.DurationSample::evidenceId))
                .toList();
        return durationEstimator.estimate(originalMinutes, samples, remainingManuallyEdited);
    }

    public List<ProfileFact> activeFacts() {
        return activeFacts(LocalDate.now(clock));
    }

    public List<ProfileFact> activeFacts(LocalDate date) {
        Objects.requireNonNull(date, "date is required");
        String dateScope = DATE_SCOPE_PREFIX + date;
        return latestFacts().values().stream()
                .filter(fact -> fact.state() == ProfileFactState.ACTIVE)
                .filter(fact -> !fact.scope().startsWith(DATE_SCOPE_PREFIX) || fact.scope().equals(dateScope))
                .sorted(Comparator.comparingLong(ProfileFact::version))
                .toList();
    }

    public List<ProfileFact> history() {
        return port.findAll().stream()
                .sorted(Comparator.comparingLong(ProfileFact::version))
                .toList();
    }

    public long profileVersion() {
        return port.findAll().stream()
                .mapToLong(ProfileFact::version)
                .max()
                .orElse(0);
    }

    public boolean isDraftCurrent(long draftProfileVersion) {
        return draftProfileVersion == profileVersion();
    }

    private ProfileFact changeState(
            String key,
            String scope,
            ProfileFactState state,
            List<String> evidenceIds) {
        requireText(key, "key");
        requireText(scope, "scope");
        ProfileFact current = latestFacts().get(factIdentity(key, scope));
        if (current == null) {
            throw new IllegalArgumentException("profile fact not found: " + key + " in " + scope);
        }
        return append(
                current.key(),
                current.value(),
                current.scope(),
                current.strength(),
                "USER_FEEDBACK",
                state,
                evidenceIds);
    }

    private ProfileFact append(
            String key,
            JsonNode value,
            String scope,
            double strength,
            String source,
            ProfileFactState state,
            List<String> evidenceIds) {
        requireText(key, "key");
        Objects.requireNonNull(value, "value is required");
        requireText(scope, "scope");
        Objects.requireNonNull(evidenceIds, "evidenceIds is required");
        ProfileFact fact = new ProfileFact(
                1,
                key,
                value,
                scope,
                strength,
                source,
                state,
                evidenceIds,
                profileVersion() + 1);
        return port.save(fact);
    }

    private Map<String, ProfileFact> latestFacts() {
        Map<String, ProfileFact> latest = new LinkedHashMap<>();
        history().forEach(fact -> latest.put(factIdentity(fact.key(), fact.scope()), fact));
        return latest;
    }

    private static DurationEstimator.DurationSample toDurationSample(ProfileFact fact) {
        JsonNode value = fact.value();
        if (!value.has("estimatedMinutes") || !value.has("actualMinutes") || !value.has("evidenceId")) {
            throw new IllegalArgumentException("duration sample fact is missing required fields: " + fact.key());
        }
        return new DurationEstimator.DurationSample(
                value.get("estimatedMinutes").intValue(),
                value.get("actualMinutes").intValue(),
                value.get("evidenceId").textValue());
    }

    private static String factIdentity(String key, String scope) {
        return key + "\u0000" + scope;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
