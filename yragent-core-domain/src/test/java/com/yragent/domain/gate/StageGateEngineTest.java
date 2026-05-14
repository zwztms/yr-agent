package com.yragent.domain.gate;

import com.yragent.domain.gate.step.CoverageReviewStep;
import com.yragent.domain.goal.GoalAnalysis;
import com.yragent.domain.memory.GateReviewAttemptSerializer;
import com.yragent.domain.memory.MemoryService;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.planning.ApproachPlan;
import com.yragent.domain.planning.PlanDocument;
import com.yragent.domain.stage.TaskExecutionContext;
import com.yragent.domain.memory.FakeMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageGateEngineTest {

    @Test
    void shouldBlockWhenRequiredDecisionsAreNotConfirmed() {
        StageGateEngine stageGateEngine = createEngine(new UnsupportedLlmClient());
        TaskExecutionContext context = new TaskExecutionContext();
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("create file"), List.of("limit workspace"), List.of("file created successfully"),
                List.of(), "medium", false));
        context.setPlanDocument(new PlanDocument("create file task", "single file approach",
                List.of("README.md"), List.of(new PlanDocument.PlanStep(1, "create file", "write_file", "create README.md", List.of("README.md"))),
                List.of("file overwrite risk"), "low"));

        GateCheckResult result = stageGateEngine.evaluate(context);

        assertFalse(result.isPassed());
        assertEquals(GateStatus.BLOCKED, result.getGateStatus());
        assertFalse(result.getPendingDecisions().isEmpty());
    }

    @Test
    void shouldPassWhenAllRequiredDecisionsAreConfirmed() {
        StageGateEngine stageGateEngine = createEngine(new FixedResponseLlmClient("""
                [
                  {"itemId":"PL-1","status":"covered","evidence":"用户复述了计划内容","suggestion":""},
                  {"itemId":"PL-2","status":"covered","evidence":"用户确认工具选择","suggestion":""},
                  {"itemId":"PL-3","status":"covered","evidence":"用户识别了风险","suggestion":""}
                ]
                """));
        TaskExecutionContext context = new TaskExecutionContext();
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("create file"), List.of("limit workspace"), List.of("file created successfully"),
                List.of(), "medium", false));
        context.setPlanDocument(new PlanDocument("create file task", "single file approach",
                List.of("README.md"), List.of(new PlanDocument.PlanStep(1, "create file", "write_file", "create README.md", List.of("README.md"))),
                List.of("file overwrite risk"), "low"));
        context.setDeveloperUnderstanding(new DeveloperUnderstanding(
                "current stage has clarified task goal and tool scope, still at gate stage pending developer confirmation before entering execution.",
                "current risk is tool authorization boundary, need to continue tightening authorization and prepare for manual takeover."
        ));
        context.confirmDecision("goal.confirm");
        context.confirmDecision("toolset.confirm");
        context.confirmDecision("risk.confirm");

        GateCheckResult result = stageGateEngine.evaluate(context);

        // UnsupportedLlmClient throws UnsupportedOperationException, which causes fallback
        // Fallback checks if developer has input -> hasInput is true -> coveragePassed=true -> PASS
        assertTrue(result.isPassed());
        assertEquals(GateStatus.PASS, result.getGateStatus());
    }

    @Test
    void shouldRequireClarificationWhenChecklistNotFullyCovered() {
        StageGateEngine stageGateEngine = createEngine(new FixedResponseLlmClient("""
                [
                  {"itemId":"PL-1","status":"covered","evidence":"用户描述了方案","suggestion":""},
                  {"itemId":"PL-2","status":"partial","evidence":"用户提到工具","suggestion":"请说明为什么选择这个工具"},
                  {"itemId":"PL-3","status":"missing","evidence":"用户未提及风险","suggestion":"你认为最大的风险是什么？"}
                ]
                """));
        TaskExecutionContext context = new TaskExecutionContext();
        context.setCurrentStage(com.yragent.domain.stage.StageType.PLANNING);
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("create file"), List.of("limit workspace"), List.of("file created successfully"),
                List.of(), "medium", false));
        context.setPlanDocument(new PlanDocument("create file task", "single file approach",
                List.of("README.md"), List.of(new PlanDocument.PlanStep(1, "create file", "write_file", "create README.md", List.of("README.md"))),
                List.of("file overwrite risk"), "low"));
        context.setDeveloperUnderstanding(new DeveloperUnderstanding(
                "current stage has clarified task goal, I think we can proceed to execution directly.",
                "current risk is mainly tool selection."
        ));
        context.confirmDecision("goal.confirm");
        context.confirmDecision("toolset.confirm");
        context.confirmDecision("risk.confirm");

        GateCheckResult result = stageGateEngine.evaluate(context);

        assertEquals(GateStatus.NEEDS_CLARIFICATION, result.getGateStatus());
        assertFalse(result.isPassed());
        assertTrue(result.getPendingDecisions().size() >= 1);
    }

    @Test
    void shouldRecordGateReviewAttemptAfterEvaluation() {
        StageGateEngine stageGateEngine = createEngine(new UnsupportedLlmClient());
        TaskExecutionContext context = new TaskExecutionContext();
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("create file"), List.of("limit workspace"), List.of("file created successfully"),
                List.of(), "medium", false));
        context.setPlanDocument(new PlanDocument("create file task", "single file approach",
                List.of("README.md"), List.of(new PlanDocument.PlanStep(1, "create file", "write_file", "create README.md", List.of("README.md"))),
                List.of("file overwrite risk"), "low"));

        stageGateEngine.evaluate(context);

        assertEquals(1, context.getGateReviewAttempts().size());
        GateReviewAttempt attempt = context.getGateReviewAttempts().get(0);
        assertEquals(1, attempt.getAttemptIndex());
        assertNotNull(attempt.getTimestamp());
        // V2.1: checklist-based gate, semanticResult is null (replaced by checklist scores)
    }

    @Test
    void shouldAccumulateMultipleAttemptsAcrossRounds() {
        StageGateEngine stageGateEngine = createEngine(new UnsupportedLlmClient());
        TaskExecutionContext context = new TaskExecutionContext();
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("create file"), List.of("limit workspace"), List.of("file created successfully"),
                List.of(), "medium", false));
        context.setPlanDocument(new PlanDocument("create file task", "single file approach",
                List.of("README.md"), List.of(new PlanDocument.PlanStep(1, "create file", "write_file", "create README.md", List.of("README.md"))),
                List.of("file overwrite risk"), "low"));

        // round 1: no developer input, blocked.
        stageGateEngine.evaluate(context);
        assertEquals(1, context.getGateReviewAttempts().size());

        // round 2: supplement input and confirmation, pass.
        context.setDeveloperUnderstanding(new DeveloperUnderstanding(
                "current stage has clarified task goal and tool scope, still at gate stage waiting for developer confirmation.",
                "risk is tool authorization boundary, need to continue tightening authorization and prepare for manual takeover."
        ));
        context.confirmDecision("goal.confirm");
        context.confirmDecision("toolset.confirm");
        context.confirmDecision("risk.confirm");
        stageGateEngine.evaluate(context);

        assertEquals(2, context.getGateReviewAttempts().size());
        assertEquals(2, context.getGateReviewAttempts().get(1).getAttemptIndex());
    }

    @Test
    void shouldIncludeEvidenceInGateReviewNote() {
        StageGateEngine stageGateEngine = createEngine(new UnsupportedLlmClient());
        TaskExecutionContext context = new TaskExecutionContext();
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("create file"), List.of("limit workspace"), List.of("file created successfully"),
                List.of(), "medium", false));
        context.setPlanDocument(new PlanDocument("create file task", "single file approach",
                List.of("README.md"), List.of(new PlanDocument.PlanStep(1, "create file", "write_file", "create README.md", List.of("README.md"))),
                List.of("file overwrite risk"), "low"));
        context.setDeveloperUnderstanding(new DeveloperUnderstanding(
                "current stage has clarified task goal and tool scope, gate confirmation not yet complete cannot enter execution.",
                "risk is tool authorization boundary, need to continue tightening authorization and prepare for manual takeover."
        ));
        context.confirmDecision("goal.confirm");
        context.confirmDecision("toolset.confirm");
        context.confirmDecision("risk.confirm");

        GateCheckResult result = stageGateEngine.evaluate(context);

        GateReviewNote reviewNote = result.getGateReviewNote();
        assertNotNull(reviewNote);
        assertNotNull(reviewNote.getEvidence());
        assertFalse(reviewNote.getEvidence().isEmpty());
    }

    private MemoryService memoryService;
    private GateReviewAttemptSerializer attemptSerializer;

    @BeforeEach
    void setUp() {
        memoryService = new MemoryService(new FakeMemoryRepository(),
                new com.yragent.domain.memory.PreferenceSerializer(),
                new com.yragent.domain.memory.PolicySerializer());
        attemptSerializer = new GateReviewAttemptSerializer();
    }

    private StageGateEngine createEngine(LlmClient llmClient) {
        return new StageGateEngine(
                new CoverageReviewStep(llmClient),
                memoryService,
                attemptSerializer
        );
    }

    @Test
    void shouldPersistGateAttemptToMemoryAfterEvaluation() {
        StageGateEngine stageGateEngine = createEngine(new UnsupportedLlmClient());
        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskId("task-persist-test");
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("create file"), List.of("limit workspace"), List.of("file created successfully"),
                List.of(), "medium", false));
        context.setPlanDocument(new PlanDocument("create file task", "single file approach",
                List.of("README.md"), List.of(new PlanDocument.PlanStep(1, "create file", "write_file", "create README.md", List.of("README.md"))),
                List.of("file overwrite risk"), "low"));
        context.setDeveloperUnderstanding(new DeveloperUnderstanding(
                "task goal and tool scope clarified, gate confirmation not yet complete.",
                "risk is tool authorization boundary, need to continue tightening authorization and prepare for manual takeover."
        ));
        context.confirmDecision("goal.confirm");
        context.confirmDecision("toolset.confirm");
        context.confirmDecision("risk.confirm");

        stageGateEngine.evaluate(context);

        // gate history should have been persisted
        var fragments = memoryService.findByTypeAndTaskId(
                com.yragent.domain.memory.MemoryType.GATE_ATTEMPT, "task-persist-test");
        assertFalse(fragments.isEmpty());
        // verify deserialization
        GateReviewAttempt deserialized = attemptSerializer.deserialize(fragments.get(0).getContent());
        assertEquals(GateStatus.PASS, deserialized.getFinalStatus());
        assertEquals(1, deserialized.getAttemptIndex());
    }

    @Test
    void shouldBlockWhenBothGoalAndPlanAreEmpty() {
        StageGateEngine stageGateEngine = createEngine(new UnsupportedLlmClient());
        TaskExecutionContext context = new TaskExecutionContext();

        GateCheckResult result = stageGateEngine.evaluate(context);

        assertEquals(GateStatus.BLOCKED, result.getGateStatus());
        assertFalse(result.isPassed());
        assertTrue(result.getSummary().contains("missing upstream output"));
    }

    @Test
    void shouldWorkWithOldApproachPlanFallback() {
        StageGateEngine stageGateEngine = createEngine(new UnsupportedLlmClient());
        TaskExecutionContext context = new TaskExecutionContext();
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("create file"), List.of("limit workspace"), List.of("file created successfully"),
                List.of(), "medium", false));
        context.setApproachPlan(new ApproachPlan("use write_file tool", List.of("write_file"), List.of("file overwrite risk"), "low"));

        // should not immediately BLOCK since approachPlan exists
        GateCheckResult result = stageGateEngine.evaluate(context);
        // should go through semantic review (which falls back)
        assertNotNull(result);
    }

    private static class UnsupportedLlmClient implements LlmClient {

        @Override
        public String chatCompletion(String prompt) {
            throw new UnsupportedOperationException("chat is not implemented");
        }

        @Override
        public String chatCompletion(List<Map<String, String>> messages) {
            throw new UnsupportedOperationException("chat is not implemented");
        }

        @Override
        public String structuredCompletion(String prompt, String schema) {
            throw new UnsupportedOperationException("structured review is not implemented");
        }

        @Override
        public String structuredCompletion(List<Map<String, String>> messages, String schema) {
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
        public String chatCompletion(List<Map<String, String>> messages) {
            return response;
        }

        @Override
        public String structuredCompletion(String prompt, String schema) {
            return response;
        }

        @Override
        public String structuredCompletion(List<Map<String, String>> messages, String schema) {
            return response;
        }
    }
}
