package com.yragent.integration.llm.deepseek;

import com.yragent.domain.gate.DeveloperUnderstanding;
import com.yragent.domain.gate.GateStatus;
import com.yragent.domain.gate.GateSemanticFindingMapper;
import com.yragent.domain.gate.GateSemanticReviewResult;
import com.yragent.domain.gate.PendingDecision;
import com.yragent.domain.gate.PendingDecisionType;
import com.yragent.domain.gate.RuleGateReviewResult;
import com.yragent.domain.gate.step.GateSemanticReviewStep;
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
        GateSemanticReviewStep step = new GateSemanticReviewStep(llmClient, new GateSemanticFindingMapper());

        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskId(UUID.randomUUID().toString());
        context.setCurrentStage(StageType.GATE_CONFIRM);
        context.addStageNote("GOAL_DEFINITION: goal defined with memory-snapshot-for-GOAL_DEFINITION");
        context.addStageNote("PLANNING: planning skeleton generated, tools=3");
        context.setDeveloperUnderstanding(new DeveloperUnderstanding(
                "The task goal is defined, the planning stage selected a candidate tool range, and the process is still blocked at the gate stage pending developer confirmation before execution.",
                "The main risk is overly broad tool authorization, so the authorization should stay tightened and human takeover should remain available."
        ));

        RuleGateReviewResult ruleResult = new RuleGateReviewResult(
                GateStatus.NEEDS_CONFIRMATION,
                "Current stage finished goal definition and planning, and now waits for developer confirmation before execution.",
                true,
                false,
                List.of(
                        new PendingDecision(PendingDecisionType.CONFIRMATION, "goal.confirm", "确认任务目标", "desc", true),
                        new PendingDecision(PendingDecisionType.CONFIRMATION, "toolset.confirm", "确认候选工具范围", "desc", true),
                        new PendingDecision(PendingDecisionType.CONFIRMATION, "risk.confirm", "确认风险与授权口径", "desc", true)
                ),
                List.of(),
                null
        );

        GateSemanticReviewResult result = step.review(context, ruleResult);

        assertTrue(result.isReviewApplied());
        assertFalse(result.isFallbackToRuleOnly());
    }

    private boolean hasDeepSeekApiKey() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        return apiKey != null && !apiKey.isBlank();
    }
}
