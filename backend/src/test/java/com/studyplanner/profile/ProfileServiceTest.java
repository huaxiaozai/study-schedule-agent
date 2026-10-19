package com.studyplanner.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.TextNode;
import com.studyplanner.contract.ProfileFact;
import com.studyplanner.contract.ProfileFactState;

class ProfileServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-13T01:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    private final InMemoryProfileFactPort port = new InMemoryProfileFactPort();
    private final ProfileService service = new ProfileService(port, new DurationEstimator(), CLOCK);

    @Test
    void storesExplicitPreferenceAndDateScopedExceptionWithEvidence() {
        ProfileFact preference = service.setExplicitPreference(
                "planning.preferredStudyPeriod",
                TextNode.valueOf("EVENING"),
                "GLOBAL",
                List.of("conversation-1"));
        ProfileFact exception = service.setTemporaryException(
                "planning.preferredStudyPeriod",
                TextNode.valueOf("MORNING"),
                LocalDate.parse("2026-09-14"),
                List.of("conversation-2"));

        assertThat(preference.source()).isEqualTo("USER_EXPLICIT");
        assertThat(preference.strength()).isEqualTo(1.0);
        assertThat(preference.version()).isEqualTo(1);
        assertThat(exception.scope()).isEqualTo("DATE:2026-09-14");
        assertThat(exception.version()).isEqualTo(2);
        assertThat(service.activeFacts(LocalDate.parse("2026-09-14")))
                .extracting(ProfileFact::value)
                .containsExactly(TextNode.valueOf("EVENING"), TextNode.valueOf("MORNING"));
        assertThat(service.activeFacts(LocalDate.parse("2026-09-15")))
                .extracting(ProfileFact::value)
                .containsExactly(TextNode.valueOf("EVENING"));
    }

    @Test
    void modifyingPreferenceSupersedesEarlierValue() {
        service.setExplicitPreference(
                "planning.preferredStudyPeriod",
                TextNode.valueOf("MORNING"),
                "GLOBAL",
                List.of("conversation-1"));
        ProfileFact updated = service.setExplicitPreference(
                "planning.preferredStudyPeriod",
                TextNode.valueOf("EVENING"),
                "GLOBAL",
                List.of("conversation-2"));

        assertThat(updated.version()).isEqualTo(2);
        assertThat(service.activeFacts(LocalDate.parse("2026-09-13")))
                .singleElement()
                .extracting(ProfileFact::value)
                .isEqualTo(TextNode.valueOf("EVENING"));
        assertThat(service.history()).hasSize(2);
    }

    @Test
    void allowsUserToDenyAndDeleteFactsWithoutErasingHistory() {
        ProfileFact preference = service.setExplicitPreference(
                "planning.preferredStudyPeriod",
                TextNode.valueOf("EVENING"),
                "GLOBAL",
                List.of("conversation-1"));

        ProfileFact denied = service.denyFact(preference.key(), preference.scope(), List.of("feedback-1"));
        ProfileFact deleted = service.deleteFact(preference.key(), preference.scope(), List.of("feedback-2"));

        assertThat(denied.state()).isEqualTo(ProfileFactState.DENIED);
        assertThat(deleted.state()).isEqualTo(ProfileFactState.DELETED);
        assertThat(service.activeFacts(LocalDate.parse("2026-09-13"))).isEmpty();
        assertThat(service.history()).extracting(ProfileFact::state)
                .containsExactly(ProfileFactState.ACTIVE, ProfileFactState.DENIED, ProfileFactState.DELETED);
    }

    @Test
    void deletingDurationEvidenceRecomputesEstimateAndInvalidatesOldDraftVersion() {
        service.recordDurationSample("course-1", 100, 200, "task-1");
        service.recordDurationSample("course-1", 100, 200, "task-2");
        ProfileFact third = service.recordDurationSample("course-1", 100, 200, "task-3");
        long draftProfileVersion = service.profileVersion();

        DurationEstimator.DurationEstimate before = service.estimateDuration("course-1", 100, false);
        ProfileFact deletion = service.deleteFact(third.key(), third.scope(), List.of("user-delete"));
        DurationEstimator.DurationEstimate after = service.estimateDuration("course-1", 100, false);

        assertThat(before.minutes()).isEqualTo(160);
        assertThat(before.sampleCount()).isEqualTo(3);
        assertThat(after.minutes()).isEqualTo(100);
        assertThat(after.sampleCount()).isEqualTo(2);
        assertThat(deletion.version()).isEqualTo(4);
        assertThat(service.isDraftCurrent(draftProfileVersion)).isFalse();
        assertThat(service.isDraftCurrent(service.profileVersion())).isTrue();
    }

    @Test
    void currentDateViewUsesInjectedClock() {
        service.setTemporaryException(
                "planning.preferredStudyPeriod",
                TextNode.valueOf("MORNING"),
                LocalDate.parse("2026-09-13"),
                List.of("conversation-1"));

        assertThat(service.activeFacts()).singleElement()
                .extracting(ProfileFact::scope)
                .isEqualTo("DATE:2026-09-13");
    }

    private static final class InMemoryProfileFactPort implements ProfileFactPort {

        private final List<ProfileFact> facts = new ArrayList<>();

        @Override
        public List<ProfileFact> findAll() {
            return List.copyOf(facts);
        }

        @Override
        public ProfileFact save(ProfileFact fact) {
            facts.add(fact);
            return fact;
        }
    }
}
