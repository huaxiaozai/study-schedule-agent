package com.studyplanner.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.agent.AgentResult;
import com.studyplanner.agent.AgentService;
import com.studyplanner.agent.AgentToolRegistry;
import com.studyplanner.agent.MockModelAdapter;
import com.studyplanner.agent.ModelAdapter;
import com.studyplanner.contract.ActionStatus;
import com.studyplanner.contract.ActionType;
import com.studyplanner.contract.ScheduleSnapshot;
import com.studyplanner.planning.CandidateBlock;
import com.studyplanner.planning.GreedyScheduler;
import com.studyplanner.planning.HardConstraintValidator;
import com.studyplanner.planning.ScheduleCandidate;
import com.studyplanner.profile.DurationEstimator;

class SchedulingActionFlowTest {

    private static final Path DEMO_SAMPLE = Path.of("..", "samples", "demo", "fixed-schedule-demo.json");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void fixedOfflineInputProducesDeterministicScheduleActionPreviewAndDurationFeedback() throws IOException {
        JsonNode demo = objectMapper.readTree(Files.readString(DEMO_SAMPLE));
        Instant fixedClock = Instant.parse(demo.path("fixedClock").asText());
        ScheduleSnapshot snapshot = objectMapper.treeToValue(demo.path("snapshot"), ScheduleSnapshot.class);
        GreedyScheduler scheduler = new GreedyScheduler(
                new HardConstraintValidator(),
                Clock.fixed(fixedClock, ZoneOffset.UTC));

        ScheduleCandidate candidate = scheduler.schedule(snapshot);

        assertThat(candidate.gaps()).isEmpty();
        assertCandidateBlocks(candidate.blocks(), demo.path("expected").path("candidateBlocks"));

        JsonNode request = demo.path("agentRequest");
        String instruction = request.path("instruction").asText();
        ModelAdapter.ModelResponse modelResponse = modelResponse(request.path("modelResponse"));
        AgentService agent = new AgentService(
                new MockModelAdapter(Map.of(instruction, modelResponse)),
                new AgentToolRegistry(scheduler));

        AgentResult result = agent.handle(request.path("operationKey").asText(), instruction, snapshot);

        JsonNode expected = demo.path("expected");
        assertThat(result.status()).isEqualTo(ActionStatus.valueOf(expected.path("actionStatus").asText()));
        assertThat(result.action().baseRevision()).isEqualTo(expected.path("baseRevision").asLong());
        assertThat(result.action().type()).isEqualTo(ActionType.MOVE_TASK);
        assertThat(result.action().targets()).containsExactly("task-001");
        assertThat(expected.path("executorPending").asBoolean()).isTrue();

        DurationEstimator.DurationEstimate estimate = durationEstimate(demo.path("durationFeedback"));
        assertThat(estimate.minutes()).isEqualTo(expected.path("adjustedDurationMinutes").asInt());
        assertThat(estimate.multiplier()).isEqualTo(expected.path("durationMultiplier").asDouble());
    }

    private static void assertCandidateBlocks(List<CandidateBlock> actual, JsonNode expected) {
        assertThat(actual).hasSize(expected.size());
        for (int index = 0; index < expected.size(); index++) {
            JsonNode expectedBlock = expected.get(index);
            CandidateBlock actualBlock = actual.get(index);
            assertThat(actualBlock.taskId()).isEqualTo(expectedBlock.path("taskId").asText());
            assertThat(actualBlock.start()).isEqualTo(Instant.parse(expectedBlock.path("start").asText()));
            assertThat(actualBlock.end()).isEqualTo(Instant.parse(expectedBlock.path("end").asText()));
        }
    }

    private static ModelAdapter.ModelResponse modelResponse(JsonNode node) {
        List<String> targets = strings(node.path("targets"));
        Map<String, JsonNode> changes = new LinkedHashMap<>();
        node.path("changes").properties().forEach(entry -> changes.put(entry.getKey(), entry.getValue()));
        return new ModelAdapter.ModelResponse(
                ActionType.valueOf(node.path("type").asText()),
                targets,
                changes,
                strings(node.path("reasonFacts")),
                strings(node.path("ambiguities")));
    }

    private static DurationEstimator.DurationEstimate durationEstimate(JsonNode node) {
        List<DurationEstimator.DurationSample> samples = new ArrayList<>();
        node.path("samples").forEach(sample -> samples.add(new DurationEstimator.DurationSample(
                sample.path("estimatedMinutes").asInt(),
                sample.path("actualMinutes").asInt(),
                sample.path("evidenceId").asText())));
        return new DurationEstimator().estimate(
                node.path("originalEstimateMinutes").asInt(),
                samples,
                false);
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }
}
