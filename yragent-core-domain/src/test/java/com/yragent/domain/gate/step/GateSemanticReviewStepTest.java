package com.yragent.domain.gate.step;

import com.yragent.domain.gate.DeveloperUnderstanding;
import com.yragent.domain.gate.GateSemanticReviewResult;
import com.yragent.domain.goal.GoalAnalysis;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.planning.PlanDocument;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateSemanticReviewStepTest {

    @Test
    void shouldFallbackToRuleOnlyWhenStructuredReviewIsUnsupported() {
        GateSemanticReviewStep step = new GateSemanticReviewStep(new UnsupportedLlmClient());
        TaskExecutionContext context = createContext();

        GateSemanticReviewResult result = step.review(context);

        assertFalse(result.isReviewApplied());
        assertTrue(result.isFallbackToRuleOnly());
    }

    @Test
    void shouldParseStructuredSemanticReviewResult() {
        GateSemanticReviewStep step = new GateSemanticReviewStep(new FixedResponseLlmClient("""
                {
                  "gateStatus": "NEEDS_INFO",
                  "reason": "developer did not cover tool scope",
                  "developerSummary": "understands goal but not tools",
                  "missingInfo": ["tool scope"],
                  "questions": ["please explain tool authorization scope"],
                  "risksIdentified": [],
                  "risksMissed": ["no mention of manual takeover conditions"]
                }
                """));
        TaskExecutionContext context = createContext();

        GateSemanticReviewResult result = step.review(context);

        assertTrue(result.isReviewApplied());
        assertFalse(result.isFallbackToRuleOnly());
        assertTrue(result.requiresClarification());
        assertTrue(result.getFeedbackItems().stream().anyMatch(item -> item.contains("tool scope")));
        assertTrue(result.getFeedbackItems().stream().anyMatch(item -> item.contains("understands goal")));
    }

    @Test
    void shouldHandlePassResponse() {
        GateSemanticReviewStep step = new GateSemanticReviewStep(new FixedResponseLlmClient("""
                {
                  "gateStatus": "PASS",
                  "reason": "developer understands well",
                  "developerSummary": "good understanding of goal and tools",
                  "missingInfo": [],
                  "questions": [],
                  "risksIdentified": ["tool authorization", "manual takeover"],
                  "risksMissed": []
                }
                """));
        TaskExecutionContext context = createContext();

        GateSemanticReviewResult result = step.review(context);

        assertTrue(result.isReviewApplied());
        assertFalse(result.isFallbackToRuleOnly());
        assertTrue(result.isCoveragePassed());
        assertFalse(result.requiresClarification());
    }

    @Test
    void shouldHandleBlockedResponse() {
        GateSemanticReviewStep step = new GateSemanticReviewStep(new FixedResponseLlmClient("""
                {
                  "gateStatus": "BLOCKED",
                  "reason": "developer input is empty",
                  "developerSummary": "no meaningful input provided",
                  "missingInfo": ["developer stage understanding"],
                  "questions": ["please describe your understanding of the current stage"],
                  "risksIdentified": [],
                  "risksMissed": ["all risks"]
                }
                """));
        TaskExecutionContext context = createContext();

        GateSemanticReviewResult result = step.review(context);

        assertTrue(result.isReviewApplied());
        assertFalse(result.isFallbackToRuleOnly());
        assertFalse(result.isCoveragePassed());
        assertTrue(result.requiresClarification());
    }

    @Test
    void shouldUseDefaultValuesWhenOptionalFieldsAreMissing() {
        GateSemanticReviewStep step = new GateSemanticReviewStep(new FixedResponseLlmClient("""
                {
                  "gateStatus": "BLOCKED",
                  "reason": "",
                  "developerSummary": "",
                  "missingInfo": [],
                  "questions": [],
                  "risksIdentified": [],
                  "risksMissed": []
                }
                """));
        TaskExecutionContext context = createContext();

        GateSemanticReviewResult result = step.review(context);

        assertTrue(result.isReviewApplied());
        assertFalse(result.isFallbackToRuleOnly());
        // BLOCKED status means coveragePassed=false
        assertFalse(result.isCoveragePassed());
    }

    @Test
    void shouldReturnBlockedWhenGoalAndPlanAreBothEmpty() {
        GateSemanticReviewStep step = new GateSemanticReviewStep(new UnsupportedLlmClient());
        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskId("task-empty");
        context.setCurrentStage(StageType.GATE_CONFIRM);

        GateSemanticReviewResult result = step.review(context);

        assertTrue(result.isReviewApplied());
        assertFalse(result.isCoveragePassed());
        assertTrue(result.getMissingTopics().stream().anyMatch(t -> t.contains("目标")));
    }

    private TaskExecutionContext createContext() {
        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskId("task-1");
        context.setCurrentStage(StageType.GATE_CONFIRM);
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("create file"), List.of("limit workspace"), List.of("file created successfully"),
                List.of(), "medium", false));
        context.setPlanDocument(new PlanDocument("create file task", "single file approach",
                List.of("README.md"), List.of(new PlanDocument.PlanStep(1, "create file", "write_file", "create README.md", List.of("README.md"))),
                List.of("file overwrite risk"), "low"));
        context.addStageNote("PLANNING: planning skeleton generated, tools=3");
        context.setDeveloperUnderstanding(new DeveloperUnderstanding(
                "current stage has clarified task goal and gate reason.",
                "current risk is tool authorization boundary."
        ));
        return context;
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
