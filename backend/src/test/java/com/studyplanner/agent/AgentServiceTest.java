package com.studyplanner.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.studyplanner.contract.ActionStatus;
import com.studyplanner.contract.ActionType;
import com.studyplanner.contract.ScheduleSnapshot;
import com.studyplanner.contract.TaskSnapshot;
import com.studyplanner.contract.TaskStatus;
import com.studyplanner.planning.GreedyScheduler;
import com.studyplanner.planning.HardConstraintValidator;

class AgentServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T01:00:00Z");

    @Test
    void createsTaskActionForConfirmationWithoutApplyingIt() {
        ModelAdapter.ModelResponse response = response(
                ActionType.CREATE_TASK,
                List.of(),
                Map.of(
                        "title", TextNode.valueOf("完成实验报告"),
                        "deadline", TextNode.valueOf("2026-09-16T12:00:00Z"),
                        "durationMinutes", IntNode.valueOf(120)),
                List.of("用户明确要求创建任务"));
        AgentService service = service(Map.of("创建任务", response));

        AgentResult result = service.handle("op-create-1", "创建任务", snapshot());

        assertThat(result.status()).isEqualTo(ActionStatus.NEEDS_CONFIRMATION);
        assertThat(result.action()).isNotNull();
        assertThat(result.action().operationKey()).isEqualTo("op-create-1");
        assertThat(result.action().baseRevision()).isEqualTo(7);
        assertThat(result.action().type()).isEqualTo(ActionType.CREATE_TASK);
        assertThat(result.action().changes()).containsEntry("durationMinutes", IntNode.valueOf(120));
        assertThat(result.action().authorizationScope()).isEqualTo("schedule:write:self");
        assertThat(result.scheduleCandidate()).isNull();
    }

    @Test
    void createsMoveActionForConfirmation() {
        ModelAdapter.ModelResponse response = response(
                ActionType.MOVE_TASK,
                List.of("task-1"),
                Map.of(
                        "start", TextNode.valueOf("2026-09-14T11:00:00Z"),
                        "end", TextNode.valueOf("2026-09-14T12:00:00Z")),
                List.of("避开课程时间"));
        AgentService service = service(Map.of("移动任务", response));

        AgentResult result = service.handle("op-move-1", "移动任务", snapshot());

        assertThat(result.status()).isEqualTo(ActionStatus.NEEDS_CONFIRMATION);
        assertThat(result.action().type()).isEqualTo(ActionType.MOVE_TASK);
        assertThat(result.action().targets()).containsExactly("task-1");
    }

    @Test
    void createsCancelActionForConfirmation() {
        ModelAdapter.ModelResponse response = response(
                ActionType.CANCEL_TASK,
                List.of("task-1"),
                Map.of(),
                List.of("用户明确要求取消"));
        AgentService service = service(Map.of("取消任务", response));

        AgentResult result = service.handle("op-cancel-1", "取消任务", snapshot());

        assertThat(result.status()).isEqualTo(ActionStatus.NEEDS_CONFIRMATION);
        assertThat(result.action().type()).isEqualTo(ActionType.CANCEL_TASK);
        assertThat(result.action().targets()).containsExactly("task-1");
    }

    @Test
    void asksForClarificationWithoutSelectingAToolWhenInformationIsMissing() {
        CountingModelAdapter adapter = new CountingModelAdapter(new MockModelAdapter(Map.of(
                "帮我安排作业",
                ModelAdapter.ModelResponse.clarification(
                        ActionType.CREATE_TASK,
                        List.of("deadline", "durationMinutes")))));
        CountingToolRegistry registry = new CountingToolRegistry(scheduler());
        AgentService service = new AgentService(adapter, registry);

        AgentResult result = service.handle("op-clarify-1", "帮我安排作业", snapshot());

        assertThat(result.status()).isEqualTo(ActionStatus.NEEDS_CLARIFICATION);
        assertThat(result.action()).isNull();
        assertThat(result.message()).contains("deadline", "durationMinutes");
        assertThat(adapter.invocations).isEqualTo(1);
        assertThat(registry.invocations).isZero();
    }

    @Test
    void invokesModelAndOneRegisteredToolAtMostOnce() {
        CountingModelAdapter adapter = new CountingModelAdapter(new MockModelAdapter(Map.of(
                "生成日程",
                response(ActionType.GENERATE_SCHEDULE, List.of(), Map.of(), List.of("固定输入排程")))));
        CountingToolRegistry registry = new CountingToolRegistry(scheduler());
        AgentService service = new AgentService(adapter, registry);

        AgentResult result = service.handle("op-plan-1", "生成日程", snapshot());

        assertThat(result.status()).isEqualTo(ActionStatus.APPLIED);
        assertThat(result.scheduleCandidate()).isNotNull();
        assertThat(result.scheduleCandidate().blocks()).hasSize(1);
        assertThat(adapter.invocations).isEqualTo(1);
        assertThat(registry.invocations).isEqualTo(1);
        assertThat(registry.registeredTools()).isEqualTo(Set.of(ActionType.values()));
    }

    @Test
    void convertsMissingToolFieldsIntoStructuredClarification() {
        AgentService service = service(Map.of(
                "移动但没说时间",
                response(ActionType.MOVE_TASK, List.of("task-1"), Map.of(), List.of())));

        AgentResult result = service.handle("op-missing-1", "移动但没说时间", snapshot());

        assertThat(result.status()).isEqualTo(ActionStatus.NEEDS_CLARIFICATION);
        assertThat(result.action()).isNotNull();
        assertThat(result.action().status()).isEqualTo(ActionStatus.NEEDS_CLARIFICATION);
        assertThat(result.action().errors()).singleElement()
                .satisfies(error -> {
                    assertThat(error.code()).isEqualTo("MISSING_ACTION_FIELDS");
                    assertThat(error.objectIds()).containsExactly("start", "end");
                });
    }

    @Test
    void returnsFailedWhenModelAdapterThrows() {
        ModelAdapter failingAdapter = (instruction, schedule) -> {
            throw new IllegalStateException("external model unavailable");
        };
        AgentService service = new AgentService(failingAdapter, new AgentToolRegistry(scheduler()));

        AgentResult result = service.handle("op-failed-1", "任意指令", snapshot());

        assertThat(result.status()).isEqualTo(ActionStatus.FAILED);
        assertThat(result.action()).isNull();
        assertThat(result.message()).isEqualTo("MODEL_ADAPTER_FAILED");
    }

    private static AgentService service(Map<String, ModelAdapter.ModelResponse> responses) {
        return new AgentService(new MockModelAdapter(responses), new AgentToolRegistry(scheduler()));
    }

    private static GreedyScheduler scheduler() {
        return new GreedyScheduler(
                new HardConstraintValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ModelAdapter.ModelResponse response(
            ActionType type,
            List<String> targets,
            Map<String, JsonNode> changes,
            List<String> reasonFacts) {
        return new ModelAdapter.ModelResponse(type, targets, changes, reasonFacts, List.of());
    }

    private static ScheduleSnapshot snapshot() {
        TaskSnapshot task = new TaskSnapshot(
                "task-1",
                "course-1",
                "完成作业",
                Instant.parse("2026-09-16T12:00:00Z"),
                30,
                3,
                TaskStatus.TODO,
                List.of());
        return new ScheduleSnapshot(
                1,
                NOW,
                "Asia/Shanghai",
                List.of(),
                List.of(task),
                List.of(),
                List.of(),
                7);
    }

    private static final class CountingModelAdapter implements ModelAdapter {

        private final ModelAdapter delegate;
        private int invocations;

        private CountingModelAdapter(ModelAdapter delegate) {
            this.delegate = delegate;
        }

        @Override
        public ModelResponse generate(String instruction, ScheduleSnapshot snapshot) {
            invocations++;
            return delegate.generate(instruction, snapshot);
        }
    }

    private static final class CountingToolRegistry extends AgentToolRegistry {

        private int invocations;

        private CountingToolRegistry(GreedyScheduler scheduler) {
            super(scheduler);
        }

        @Override
        public AgentResult execute(
                String operationKey,
                ScheduleSnapshot snapshot,
                ModelAdapter.ModelResponse response) {
            invocations++;
            return super.execute(operationKey, snapshot, response);
        }
    }
}
