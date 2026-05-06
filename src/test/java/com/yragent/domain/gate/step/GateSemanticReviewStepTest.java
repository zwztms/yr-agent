package com.yragent.domain.gate.step;

import com.yragent.domain.gate.DeveloperUnderstanding;
import com.yragent.domain.gate.GateSemanticReviewResult;
import com.yragent.domain.gate.GateStatus;
import com.yragent.domain.gate.RuleGateReviewResult;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateSemanticReviewStepTest {

    @Test
    void shouldFallbackToRuleOnlyWhenStructuredReviewIsUnsupported() {
        GateSemanticReviewStep step = new GateSemanticReviewStep(new UnsupportedLlmClient(), new com.yragent.domain.gate.GateSemanticFindingMapper());
        TaskExecutionContext context = createContext();
        RuleGateReviewResult ruleResult = createRuleResult(GateStatus.PASS);

        GateSemanticReviewResult result = step.review(context, ruleResult);

        assertFalse(result.isReviewApplied());
        assertTrue(result.isFallbackToRuleOnly());
    }

    @Test
    void shouldParseStructuredSemanticReviewResult() {
        GateSemanticReviewStep step = new GateSemanticReviewStep(new FixedResponseLlmClient("""
                {
                  "coveragePassed": false,
                  "coveredTopics": ["任务目标"],
                  "missingTopics": ["工具范围"],
                  "misunderstandings": ["把门禁确认当成已授权"],
                  "riskBlindSpots": ["未说明人工接管条件"],
                  "suggestedQuestions": ["请补充说明风险收紧口径"],
                  "rawSummary": "发现明显理解缺口"
                }
                """), new com.yragent.domain.gate.GateSemanticFindingMapper());
        TaskExecutionContext context = createContext();
        RuleGateReviewResult ruleResult = createRuleResult(GateStatus.PASS);

        GateSemanticReviewResult result = step.review(context, ruleResult);

        assertTrue(result.isReviewApplied());
        assertFalse(result.isFallbackToRuleOnly());
        assertTrue(result.requiresClarification());
        assertTrue(result.getFeedbackItems().stream().anyMatch(item -> item.contains("工具范围")));
        assertTrue(result.getFeedbackItems().stream().anyMatch(item -> item.contains("人工接管")));
    }

    @Test
    void shouldExtractJsonWhenModelWrapsResponseWithMarkdownAndText() {
        GateSemanticReviewStep step = new GateSemanticReviewStep(new FixedResponseLlmClient("""
                下面是评审结果：
                ```json
                {
                  "coveragePassed": false,
                  "missingTopics": ["当前为何停在门禁阶段"],
                  "misunderstandings": [],
                  "riskBlindSpots": [],
                  "suggestedQuestions": ["请补充解释门禁原因"],
                  "rawSummary": "存在门禁理解缺口"
                }
                ```
                请继续处理。
                """), new com.yragent.domain.gate.GateSemanticFindingMapper());
        TaskExecutionContext context = createContext();
        RuleGateReviewResult ruleResult = createRuleResult(GateStatus.PASS);

        GateSemanticReviewResult result = step.review(context, ruleResult);

        assertTrue(result.isReviewApplied());
        assertTrue(result.requiresClarification());
        assertTrue(result.getFeedbackItems().stream().anyMatch(item -> item.contains("门禁阶段")));
    }

    @Test
    void shouldUseDefaultValuesWhenOptionalFieldsAreMissing() {
        GateSemanticReviewStep step = new GateSemanticReviewStep(new FixedResponseLlmClient("""
                {
                  "rawSummary": "模型只返回了最小字段"
                }
                """), new com.yragent.domain.gate.GateSemanticFindingMapper());
        TaskExecutionContext context = createContext();
        RuleGateReviewResult ruleResult = createRuleResult(GateStatus.PASS);

        GateSemanticReviewResult result = step.review(context, ruleResult);

        assertTrue(result.isReviewApplied());
        assertFalse(result.isFallbackToRuleOnly());
        assertFalse(result.requiresClarification());
    }

    private TaskExecutionContext createContext() {
        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskId("task-1");
        context.setCurrentStage(StageType.GATE_CONFIRM);
        context.addStageNote("PLANNING: planning skeleton generated, tools=3");
        context.setDeveloperUnderstanding(new DeveloperUnderstanding(
                "当前阶段已经明确任务目标和门禁原因。",
                "当前风险在于工具授权边界。"
        ));
        return context;
    }

    private RuleGateReviewResult createRuleResult(GateStatus gateStatus) {
        return new RuleGateReviewResult(
                gateStatus,
                "当前已完成目标定义与方案规划。",
                true,
                true,
                List.of(),
                List.of(),
                null
        );
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
