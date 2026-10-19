package com.studyplanner.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.agent.AgentService;
import com.studyplanner.agent.AgentToolRegistry;
import com.studyplanner.agent.MockModelAdapter;
import com.studyplanner.agent.ModelAdapter;
import com.studyplanner.contract.ActionStatus;
import com.studyplanner.contract.ActionType;
import com.studyplanner.contract.ImportProposal;
import com.studyplanner.contract.ImportStatus;
import com.studyplanner.contract.ScheduleSnapshot;
import com.studyplanner.contract.TaskSnapshot;
import com.studyplanner.contract.TaskStatus;
import com.studyplanner.importing.ImportService;
import com.studyplanner.importing.ImportSource;
import com.studyplanner.importing.ImportSourceType;
import com.studyplanner.importing.InMemoryImportCommitPort;
import com.studyplanner.importing.InMemoryImportJobRepository;
import com.studyplanner.importing.MockImportModelAdapter;
import com.studyplanner.planning.GreedyScheduler;
import com.studyplanner.planning.HardConstraintValidator;

class ImportSchedulingFlowTest {
    @Test
    void reviewedImportSampleCanFeedOfflineSchedulingWithoutFormalPersistence() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T02:00:00Z"), ZoneOffset.UTC);
        ImportService imports = new ImportService(new MockImportModelAdapter(),
                new InMemoryImportJobRepository(), new InMemoryImportCommitPort(), clock);
        ImportProposal review = imports.submit(new ImportSource("request-demo-001", ImportSourceType.SCREENSHOT,
                "course-table.png", new byte[]{1})).proposal();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ImportProposal corrected = mapper.readValue(Files.readString(
                Path.of("..", "samples", "importing", "import-proposal-confirmed.json")), ImportProposal.class);
        assertThat(corrected.proposalId()).isEqualTo(review.proposalId());
        assertThat(imports.confirm("request-demo-001", corrected).status()).isEqualTo(ImportStatus.COMMITTED);

        // Test-only handoff mapping: the business adapter must resolve course IDs
        // and perform the actual transaction; courseCandidate is only a name.
        TaskSnapshot task = new TaskSnapshot("task-from-" + corrected.proposalId(), null,
                corrected.title(), corrected.deadline(), corrected.durationMinutes(), 3,
                TaskStatus.TODO, List.of());
        ScheduleSnapshot context = new ScheduleSnapshot(1, clock.instant(), "Asia/Shanghai", List.of(),
                List.of(task), List.of(), List.of(), 0);
        HardConstraintValidator validator = new HardConstraintValidator();
        GreedyScheduler scheduler = new GreedyScheduler(validator, clock);
        ModelAdapter.ModelResponse response = new ModelAdapter.ModelResponse(ActionType.GENERATE_SCHEDULE,
                List.of(), Map.of(), List.of(corrected.proposalId()), List.of());
        AgentService agent = new AgentService(new MockModelAdapter(Map.of("安排导入任务", response)),
                new AgentToolRegistry(scheduler));
        var result = agent.handle("import-plan-demo", "安排导入任务", context);
        assertThat(result.status()).isEqualTo(ActionStatus.APPLIED);
        assertThat(result.scheduleCandidate().gaps()).isEmpty();
        assertThat(result.scheduleCandidate().blocks()).hasSize(4).allSatisfy(block -> {
            assertThat(block.taskId()).isEqualTo(task.id());
            assertThat(validator.validate(context, block)).isEmpty();
        });
        assertThat(result.scheduleCandidate()).isEqualTo(scheduler.schedule(context));
        assertThat(imports.get("request-demo-001").proposal().sourceEvidence())
                .containsExactlyElementsOf(corrected.sourceEvidence());
    }
}
