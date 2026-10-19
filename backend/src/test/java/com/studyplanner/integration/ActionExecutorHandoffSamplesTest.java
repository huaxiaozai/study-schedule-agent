package com.studyplanner.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.planning.ConstraintCode;

class ActionExecutorHandoffSamplesTest {

    private static final Path CONTRACT_SAMPLES = Path.of("..", "samples", "contracts");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void versionConflictKeepsRevisionDetailsAndStableErrorShape() throws IOException {
        JsonNode response = readJson("action-conflict-response.json");

        assertThat(response.path("httpStatus").asInt()).isEqualTo(409);
        assertThat(response.path("actionStatus").asText()).isEqualTo("FAILED");
        assertThat(response.path("baseRevision").asLong()).isLessThan(response.path("currentRevision").asLong());
        assertThat(response.path("retryable").asBoolean()).isTrue();
        assertThat(response.path("error").path("code").asText()).isEqualTo("REVISION_CONFLICT");
        assertThat(response.path("error").path("objectIds")).isNotEmpty();
    }

    @Test
    void idempotentReplayReturnsTheOriginalExecutionWithoutIncrementingRevision() throws IOException {
        JsonNode sample = readJson("action-idempotent-replay.json");

        assertThat(sample.path("replayed").asBoolean()).isTrue();
        assertThat(sample.path("replayExecution")).isEqualTo(sample.path("firstExecution"));
        assertThat(sample.path("request").path("operationKey").asText())
                .isEqualTo(sample.path("firstExecution").path("operationKey").asText());
    }

    @Test
    void undoUsesANewIdempotencyKeyAndAdvancesRevisionOnce() throws IOException {
        JsonNode sample = readJson("action-undo-flow.json");
        JsonNode request = sample.path("request");
        JsonNode response = sample.path("response");

        assertThat(response.path("operationKey").asText()).isEqualTo(request.path("operationKey").asText());
        assertThat(response.path("reversedExecutionId").asText()).isEqualTo(request.path("executionId").asText());
        assertThat(response.path("planningRevision").asLong()).isEqualTo(request.path("baseRevision").asLong() + 1);
        assertThat(response.path("actionStatus").asText()).isEqualTo("APPLIED");
    }

    @Test
    void handoffDocumentListsEverySharedHardConstraintCode() throws IOException {
        String handoff = Files.readString(CONTRACT_SAMPLES.resolve("action-executor-handoff.md"));

        assertThat(ConstraintCode.values()).allSatisfy(code -> assertThat(handoff).contains("`" + code.name() + "`"));
        assertThat(handoff).contains("`REVISION_CONFLICT`", "`DUPLICATE_OPERATION_MISMATCH`", "`UNDO_CONFLICT`");
    }

    private JsonNode readJson(String filename) throws IOException {
        return objectMapper.readTree(Files.readString(CONTRACT_SAMPLES.resolve(filename)));
    }
}
