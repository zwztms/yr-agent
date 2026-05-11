package com.yragent.infrastructure.integration.llm.deepseek;

import com.yragent.domain.gate.DeveloperUnderstanding;
import com.yragent.domain.gate.GateSemanticReviewResult;
import com.yragent.domain.goal.GoalAnalysis;
import com.yragent.domain.gate.step.GateSemanticReviewStep;
import com.yragent.domain.planning.PlanDocument;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DeepSeekGateSemanticReviewIntegrationTest {

    @Test
    void shouldCallRealDeepSeekAndParseSemanticReviewResult() {
        assumeTrue(hasDeepSeekApiKey(), "Missing DEEPSEEK_API_KEY for DeepSeek integration test");

        DeepSeekCompatibleLlmClient llmClient = new DeepSeekCompatibleLlmClient(
                "https://api.deepseek.com",
                "deepseek-chat",
                "",
                "/v1/chat/completions"
        );
        GateSemanticReviewStep step = new GateSemanticReviewStep(llmClient);

        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskId(UUID.randomUUID().toString());
        context.setCurrentStage(StageType.GATE_CONFIRM);
        context.setGoalAnalysis(new GoalAnalysis("file_operation", List.of("create file"), List.of("limit workspace"), List.of("file created successfully"),
                List.of(), "medium", false));
        context.setPlanDocument(new PlanDocument("create file task", "single file approach",
                List.of("README.md"), List.of(new PlanDocument.PlanStep(1, "create file", "write_file", "create README.md", List.of("README.md"))),
                List.of("file overwrite risk"), "low"));
        context.addStageNote("GOAL_DEFINITION: goal defined with memory-snapshot-for-GOAL_DEFINITION");
        context.addStageNote("PLANNING: planning skeleton generated, tools=3");
        context.setDeveloperUnderstanding(new DeveloperUnderstanding(
                "The task goal is defined, the planning stage selected a candidate tool range, and the process is still blocked at the gate stage pending developer confirmation before execution.",
                "The main risk is overly broad tool authorization, so the authorization should stay tightened and human takeover should remain available."
        ));

        // V2: review() takes only TaskExecutionContext
        GateSemanticReviewResult result = step.review(context);

        assertTrue(result.isReviewApplied());
        assertFalse(result.isFallbackToRuleOnly());
    }

    private boolean hasDeepSeekApiKey() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        return apiKey != null && !apiKey.isBlank();
    }
}
