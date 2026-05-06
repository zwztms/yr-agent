package com.yragent.domain.gate;

import com.yragent.domain.gate.policy.GateReviewMergePolicy;
import com.yragent.domain.gate.step.GateSemanticReviewStep;
import com.yragent.domain.gate.step.RuleGateCheckStep;
import com.yragent.domain.goal.GoalAnalysis;
import com.yragent.domain.memory.GateReviewAttemptSerializer;
import com.yragent.domain.memory.MemoryService;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.planning.ApproachPlan;
import com.yragent.domain.stage.TaskExecutionContext;
import com.yragent.repository.MemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageGateEngineTest {

    @Test
    void shouldBlockWhenRequiredDecisionsAreNotConfirmed() {
        StageGateEngine stageGateEngine = createEngine(new UnsupportedLlmClient());
        TaskExecutionContext context = new TaskExecutionContext();
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("创建文件"), List.of("限定workspace"), List.of("文件创建成功")));
        context.setApproachPlan(new ApproachPlan("使用write_file工具创建文件", List.of("write_file"), List.of("文件覆盖风险"), "low"));

        GateCheckResult result = stageGateEngine.evaluate(context);

        assertFalse(result.isPassed());
        assertEquals(GateStatus.BLOCKED, result.getGateStatus());
        assertEquals(5, result.getPendingDecisions().size());
    }

    @Test
    void shouldPassWhenAllRequiredDecisionsAreConfirmed() {
        StageGateEngine stageGateEngine = createEngine(new UnsupportedLlmClient());
        TaskExecutionContext context = new TaskExecutionContext();
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("创建文件"), List.of("限定workspace"), List.of("文件创建成功")));
        context.setApproachPlan(new ApproachPlan("使用write_file工具创建文件", List.of("write_file"), List.of("文件覆盖风险"), "low"));
        context.setDeveloperUnderstanding(new DeveloperUnderstanding(
                "当前阶段已经明确任务目标、工具范围，并且因为门禁确认还不能直接进入执行阶段。",
                "当前风险在于工具授权和执行边界，必要时继续收紧授权并准备人工接管。"
        ));
        context.confirmDecision("goal.confirm");
        context.confirmDecision("toolset.confirm");
        context.confirmDecision("risk.confirm");

        GateCheckResult result = stageGateEngine.evaluate(context);

        assertTrue(result.isPassed());
        assertEquals(GateStatus.PASS, result.getGateStatus());
        assertTrue(result.getPendingDecisions().isEmpty());
    }

    @Test
    void shouldRequireClarificationWhenSemanticReviewFindsMisunderstanding() {
        StageGateEngine stageGateEngine = createEngine(new FixedResponseLlmClient("""
                {
                  "coveragePassed": false,
                  "coveredTopics": ["任务目标"],
                  "missingTopics": ["工具范围"],
                  "misunderstandings": ["把门禁确认当成系统已经授权"],
                  "riskBlindSpots": [],
                  "suggestedQuestions": ["请解释为什么当前仍停在门禁阶段"],
                  "rawSummary": "语义评审发现关键缺口"
                }
                """));
        TaskExecutionContext context = new TaskExecutionContext();
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("创建文件"), List.of("限定workspace"), List.of("文件创建成功")));
        context.setApproachPlan(new ApproachPlan("使用write_file工具创建文件", List.of("write_file"), List.of("文件覆盖风险"), "low"));
        context.setDeveloperUnderstanding(new DeveloperUnderstanding(
                "当前阶段已经明确任务目标，但我认为已经可以直接进入执行。",
                "当前风险主要是工具选择。"
        ));
        context.confirmDecision("goal.confirm");
        context.confirmDecision("toolset.confirm");
        context.confirmDecision("risk.confirm");

        GateCheckResult result = stageGateEngine.evaluate(context);

        assertEquals(GateStatus.NEEDS_CLARIFICATION, result.getGateStatus());
        assertFalse(result.isPassed());
        assertTrue(result.getPendingDecisions().stream()
                .anyMatch(decision -> decision.getType() == PendingDecisionType.UNDERSTANDING_INPUT));
    }

    @Test
    void shouldRecordGateReviewAttemptAfterEvaluation() {
        StageGateEngine stageGateEngine = createEngine(new UnsupportedLlmClient());
        TaskExecutionContext context = new TaskExecutionContext();
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("创建文件"), List.of("限定workspace"), List.of("文件创建成功")));
        context.setApproachPlan(new ApproachPlan("使用write_file工具创建文件", List.of("write_file"), List.of("文件覆盖风险"), "low"));

        stageGateEngine.evaluate(context);

        assertEquals(1, context.getGateReviewAttempts().size());
        GateReviewAttempt attempt = context.getGateReviewAttempts().get(0);
        assertEquals(1, attempt.getAttemptIndex());
        assertNotNull(attempt.getTimestamp());
        assertEquals(GateStatus.BLOCKED, attempt.getFinalStatus());
        assertNotNull(attempt.getRuleResult());
    }

    @Test
    void shouldAccumulateMultipleAttemptsAcrossRounds() {
        StageGateEngine stageGateEngine = createEngine(new UnsupportedLlmClient());
        TaskExecutionContext context = new TaskExecutionContext();
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("创建文件"), List.of("限定workspace"), List.of("文件创建成功")));
        context.setApproachPlan(new ApproachPlan("使用write_file工具创建文件", List.of("write_file"), List.of("文件覆盖风险"), "low"));

        // 第一轮：无开发者输入，阻断。
        stageGateEngine.evaluate(context);
        assertEquals(1, context.getGateReviewAttempts().size());
        assertEquals(GateStatus.BLOCKED, context.getGateReviewAttempts().get(0).getFinalStatus());

        // 第二轮：补充输入和确认，通过。
        context.setDeveloperUnderstanding(new DeveloperUnderstanding(
                "当前阶段已明确任务目标和工具范围，停在门禁阶段是为等待开发者确认。",
                "风险在于工具授权边界，需要继续收紧授权并准备人工接管。"
        ));
        context.confirmDecision("goal.confirm");
        context.confirmDecision("toolset.confirm");
        context.confirmDecision("risk.confirm");
        stageGateEngine.evaluate(context);

        assertEquals(2, context.getGateReviewAttempts().size());
        assertEquals(2, context.getGateReviewAttempts().get(1).getAttemptIndex());
        assertEquals(GateStatus.PASS, context.getGateReviewAttempts().get(1).getFinalStatus());
    }

    @Test
    void shouldIncludeEvidenceInGateReviewNote() {
        StageGateEngine stageGateEngine = createEngine(new UnsupportedLlmClient());
        TaskExecutionContext context = new TaskExecutionContext();
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("创建文件"), List.of("限定workspace"), List.of("文件创建成功")));
        context.setApproachPlan(new ApproachPlan("使用write_file工具创建文件", List.of("write_file"), List.of("文件覆盖风险"), "low"));
        context.setDeveloperUnderstanding(new DeveloperUnderstanding(
                "当前阶段已明确任务目标和工具范围，门禁确认未完成前不能进入执行。",
                "风险在于工具授权边界，需要继续收紧授权并准备人工接管。"
        ));
        context.confirmDecision("goal.confirm");
        context.confirmDecision("toolset.confirm");
        context.confirmDecision("risk.confirm");

        GateCheckResult result = stageGateEngine.evaluate(context);

        GateReviewNote reviewNote = result.getGateReviewNote();
        assertNotNull(reviewNote);
        assertNotNull(reviewNote.getEvidence());
        assertFalse(reviewNote.getEvidence().isEmpty());
        assertTrue(reviewNote.getEvidence().stream().anyMatch(e -> e.contains("合并裁决")));
    }

    private MemoryService memoryService;
    private GateReviewAttemptSerializer attemptSerializer;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:target/test-gate-engine-" + System.nanoTime() + ".db");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS memory_fragment (
                    id TEXT PRIMARY KEY NOT NULL,
                    type TEXT NOT NULL,
                    title TEXT DEFAULT '',
                    content TEXT NOT NULL,
                    priority REAL DEFAULT 0.5,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    task_id TEXT DEFAULT NULL,
                    stage TEXT DEFAULT NULL,
                    tags TEXT DEFAULT ''
                )
                """);
        memoryService = new MemoryService(new MemoryRepository(jdbcTemplate),
                new com.yragent.domain.memory.PreferenceSerializer(),
                new com.yragent.domain.memory.PolicySerializer());
        attemptSerializer = new GateReviewAttemptSerializer();
    }

    private StageGateEngine createEngine(LlmClient llmClient) {
        return new StageGateEngine(
                new RuleGateCheckStep(),
                new GateSemanticReviewStep(llmClient, new GateSemanticFindingMapper()),
                new GateReviewMergePolicy(),
                new GateCheckResultBuilder(),
                memoryService,
                attemptSerializer
        );
    }

    @Test
    void shouldPersistGateAttemptToMemoryAfterEvaluation() {
        StageGateEngine stageGateEngine = createEngine(new UnsupportedLlmClient());
        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskId("task-persist-test");
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("创建文件"), List.of("限定workspace"), List.of("文件创建成功")));
        context.setApproachPlan(new ApproachPlan("使用write_file工具创建文件", List.of("write_file"), List.of("文件覆盖风险"), "low"));
        context.setDeveloperUnderstanding(new DeveloperUnderstanding(
                "已明确任务目标和工具范围，门禁确认未完成前不进入执行。",
                "风险在于工具授权边界，需要继续收紧授权并准备人工接管。"
        ));
        context.confirmDecision("goal.confirm");
        context.confirmDecision("toolset.confirm");
        context.confirmDecision("risk.confirm");

        stageGateEngine.evaluate(context);

        // 门禁历史应已入库
        var fragments = memoryService.findByTypeAndTaskId(
                com.yragent.domain.memory.MemoryType.GATE_ATTEMPT, "task-persist-test");
        assertFalse(fragments.isEmpty());
        // 验证能反序列化回来
        GateReviewAttempt deserialized = attemptSerializer.deserialize(fragments.get(0).getContent());
        assertEquals(GateStatus.PASS, deserialized.getFinalStatus());
        assertEquals(1, deserialized.getAttemptIndex());
    }

    private static class UnsupportedLlmClient implements LlmClient {

        @Override
        public String chatCompletion(String prompt) {
            throw new UnsupportedOperationException("chat is not implemented");
        }

        @Override
        public String structuredCompletion(String prompt, String schema) {
            throw new UnsupportedOperationException("structured review is not implemented");
        }
    }

    private static class FixedResponseLlmClient implements LlmClient {

        private final String response;

        private FixedResponseLlmClient(String response) {
            this.response = response;
        }

        @Override
        public String chatCompletion(String prompt) {
            return response;
        }

        @Override
        public String structuredCompletion(String prompt, String schema) {
            return response;
        }
    }
}
