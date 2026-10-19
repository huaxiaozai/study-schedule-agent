package com.studyplanner.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.fasterxml.jackson.databind.ObjectMapper;

class ContractSerializationTest {

    private static final Path CONTRACT_SAMPLES = Path.of("..", "samples", "contracts");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    static java.util.stream.Stream<Arguments> validSamples() {
        return java.util.stream.Stream.of(
                Arguments.of("import-proposal.json", ImportProposal.class),
                Arguments.of("schedule-snapshot.json", ScheduleSnapshot.class),
                Arguments.of("action-spec.json", ActionSpec.class),
                Arguments.of("profile-fact.json", ProfileFact.class));
    }

    @ParameterizedTest
    @MethodSource("validSamples")
    <T> void sampleRoundTripsWithoutLosingFields(String filename, Class<T> type) throws IOException {
        String json = read(filename);

        T deserialized = objectMapper.readValue(json, type);
        T roundTripped = objectMapper.readValue(objectMapper.writeValueAsString(deserialized), type);

        assertThat(roundTripped).isEqualTo(deserialized);
    }

    @Test
    void usesIsoInstantAndIanaTimezone() throws IOException {
        ScheduleSnapshot snapshot = objectMapper.readValue(read("schedule-snapshot.json"), ScheduleSnapshot.class);

        assertThat(snapshot.now()).isEqualTo(Instant.parse("2026-09-13T01:00:00Z"));
        assertThat(snapshot.timezone()).isEqualTo("Asia/Shanghai");
        assertThat(objectMapper.writeValueAsString(snapshot)).contains("2026-09-13T01:00:00Z");
    }

    @Test
    void rejectsMissingRequiredField() throws IOException {
        assertThatThrownBy(() -> objectMapper.readValue(read("invalid/missing-required-field.json"), ImportProposal.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("title is required");
    }

    @Test
    void rejectsUnknownEnum() throws IOException {
        assertThatThrownBy(() -> objectMapper.readValue(read("invalid/unknown-action-type.json"), ActionSpec.class))
                .hasMessageContaining("UNKNOWN_ACTION");
    }

    @Test
    void rejectsExpiredSchemaVersion() throws IOException {
        assertThatThrownBy(() -> objectMapper.readValue(read("invalid/expired-schema-version.json"), ProfileFact.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Unsupported schemaVersion: 0; expected: 1");
    }

    private static String read(String filename) throws IOException {
        return Files.readString(CONTRACT_SAMPLES.resolve(filename));
    }
}
