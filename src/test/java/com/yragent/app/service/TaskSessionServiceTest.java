package com.yragent.app.service;

import com.yragent.domain.gate.GateReviewNote;
import com.yragent.domain.gate.GateStatus;
import com.yragent.domain.gate.PendingDecision;
import com.yragent.domain.gate.PendingDecisionType;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class TaskSessionServiceTest {

    private TaskSessionService sessionService;
    private List<PendingDecision> testDecisions;

    @BeforeEach
    void setUp() {
        TaskApplicationService taskAppService = new StubTaskApplicationService();
        sessionService = new TaskSessionService(taskAppService);
        testDecisions = List.of(
                new PendingDecision(PendingDecisionType.CONFIRMATION,
                        "goal.confirm", "确认目标", "是否确认任务目标？", true)
        );
    }

    @Test
    void shouldCreateAndRetrieveTask() {
        TaskExecutionContext context = sessionService.createTask("测试任务");

        assertNotNull(context.getTaskId());
        TaskExecutionContext retrieved = sessionService.getTask(context.getTaskId());
        assertSame(context, retrieved);
    }

    @Test
    void shouldSubmitGateInputAndUpdateContext() {
        TaskExecutionContext context = sessionService.createTask("测试任务");
        String taskId = context.getTaskId();

        TaskExecutionContext updated = sessionService.submitGateInput(
                taskId, "理解", "风险", List.of("goal.confirm"));

        assertNotNull(updated);
        assertTrue(updated.getConfirmedDecisionCodes().contains("goal.confirm"));
        // 门禁通过后应进入 EXECUTION 阶段
        assertEquals(StageType.EXECUTION, updated.getCurrentStage());
    }

    @Test
    void shouldThrowExceptionForUnknownTask() {
        assertThrows(NoSuchElementException.class,
                () -> sessionService.getTask("nonexistent-id"));
    }

    @Test
    void shouldListAllTasks() {
        sessionService.createTask("任务一");
        sessionService.createTask("任务二");

        List<TaskExecutionContext> tasks = sessionService.listTasks();
        assertEquals(2, tasks.size());
    }

    // ---- 内部桩 ----

    private static class StubTaskApplicationService extends TaskApplicationService {

        StubTaskApplicationService() {
            super(null);
        }

        @Override
        public TaskExecutionContext startTask(String userInput) {
            TaskExecutionContext context = new TaskExecutionContext();
            context.setTaskId(java.util.UUID.randomUUID().toString());
            context.setUserInput(userInput);
            context.setCurrentStage(StageType.GATE_CONFIRM);
            context.replacePendingDecisions(List.of(
                    new PendingDecision(PendingDecisionType.CONFIRMATION,
                            "goal.confirm", "确认目标", "是否确认任务目标？", true)
            ));
            context.setCurrentStageSummary("GATE_CONFIRM 阶段：确认任务目标和工具选择");
            context.setGateReviewNote(new GateReviewNote(
                    GateStatus.BLOCKED, "需要确认",
                    List.of("请确认任务目标"), List.of("gate blocked")));
            return context;
        }

        @Override
        public TaskExecutionContext submitGateInputAndContinue(
                TaskExecutionContext context, String understanding,
                String riskSummary, List<String> confirmedDecisionCodes) {
            for (String code : confirmedDecisionCodes) {
                context.confirmDecision(code);
            }
            // 模拟门禁通过，进入 EXECUTION
            context.setCurrentStage(StageType.EXECUTION);
            context.replacePendingDecisions(List.of());
            return context;
        }
    }
}
