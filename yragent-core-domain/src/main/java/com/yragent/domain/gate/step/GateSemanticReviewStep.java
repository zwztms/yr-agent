package com.yragent.domain.gate.step;

import com.yragent.domain.gate.*;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.planning.PlanDocument;
import com.yragent.domain.goal.GoalAnalysis;
import com.yragent.domain.stage.TaskExecutionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GateSemanticReviewStep {

    private static final Logger log = LoggerFactory.getLogger(GateSemanticReviewStep.class);
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GateSemanticReviewStep(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    // V2: Single unified LLM gate review. No longer takes rule result — LLM decides everything.
    public GateSemanticReviewResult review(TaskExecutionContext context) {
        GoalAnalysis goal = context.getGoalAnalysis();
        PlanDocument plan = context.getPlanDocument();
        DeveloperUnderstanding devUnderstanding = context.getDeveloperUnderstanding();

        // Upstream validation: if no plan and no goal, cannot proceed
        if ((goal == null || goal.goals().isEmpty()) && (plan == null || plan.getSteps().isEmpty())) {
            return new GateSemanticReviewResult(
                    true, false, false,
                    List.of("目标和计划均为空"),
                    List.of("请先完成目标分析和规划阶段"),
                    List.of("无法评估"),
                    List.of("请重新输入更明确的任务描述"),
                    List.of("目标和计划均为空，无法评估"),
                    null);
        }

        try {
            String prompt = buildGatePrompt(goal, plan, devUnderstanding);
            String schema = buildGateOutputSchema();
            String rawResponse = llmClient.structuredCompletion(prompt, schema);
            return parseGateResponse(rawResponse);
        } catch (Exception e) {
            log.warn("LLM gate review failed, defaulting to rule-only", e);
            return fallbackReview(context);
        }
    }

    private String buildGatePrompt(GoalAnalysis goal, PlanDocument plan, DeveloperUnderstanding devUnderstanding) {
        return """
            你是门禁裁决者。判断开发者是否理解当前阶段设计并可以授权进入执行。

            1. 任务目标分析：%s
            2. 详细计划书：%s
            3. 开发者对设计的理解：%s
            4. 开发者对风险的判断：%s

            判断标准：
            - PASS：开发者准确理解了目标、计划、工具和风险，没有遗漏关键信息
            - NEEDS_INFO：理解有缺口但没有根本性误解，给出具体追问
            - BLOCKED：开发者输入为空、仅几个字、或存在根本性误解

            重要规则：
            - 核心判断是"开发者是否真正理解将发生什么"，不是"是否使用了特定词汇"
            - 输入为空或仅几个字必须 BLOCKED
            - 复述了计划关键内容即使表述不同也应 PASS
            - 追问必须具体可操作，如"你打算如何处理文件已存在的情况？"

            输出严格 JSON：
            {
              "gateStatus": "PASS|NEEDS_INFO|BLOCKED",
              "reason": "裁决理由（中文，简洁）",
              "developerSummary": "对开发者理解的评价",
              "missingInfo": ["需要的信息"],
              "questions": ["具体追问"],
              "risksIdentified": ["开发者正确识别的风险"],
              "risksMissed": ["开发者遗漏的风险"]
            }
            """.formatted(
                goal != null ? describeGoal(goal) : "无",
                plan != null ? describePlan(plan) : "无（使用旧版 ApproachPlan）",
                devUnderstanding != null && devUnderstanding.getStageSummary() != null
                        ? devUnderstanding.getStageSummary() : "(开发者未提供理解)",
                devUnderstanding != null && devUnderstanding.getRiskSummary() != null
                        ? devUnderstanding.getRiskSummary() : "(开发者未提供风险判断)");
    }

    private String describeGoal(GoalAnalysis goal) {
        return String.format("type=%s, goal=%s, constraints=%s, confidence=%s",
                goal.taskType(),
                String.join(";", goal.goals()),
                String.join(";", goal.constraints()),
                goal.confidence());
    }

    private String describePlan(PlanDocument plan) {
        return String.format("overview=%s, architecture=%s, steps=%d, risks=%s, complexity=%s",
                plan.getOverview(),
                plan.getArchitecture(),
                plan.getSteps().size(),
                String.join(";", plan.getRisks()),
                plan.getEstimatedComplexity());
    }

    private String buildGateOutputSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "gateStatus": {"type": "string", "enum": ["PASS", "NEEDS_INFO", "BLOCKED"]},
                "reason": {"type": "string"},
                "developerSummary": {"type": "string"},
                "missingInfo": {"type": "array", "items": {"type": "string"}},
                "questions": {"type": "array", "items": {"type": "string"}},
                "risksIdentified": {"type": "array", "items": {"type": "string"}},
                "risksMissed": {"type": "array", "items": {"type": "string"}}
              },
              "required": ["gateStatus", "reason", "developerSummary", "missingInfo", "questions", "risksIdentified", "risksMissed"]
            }""";
    }

    private GateSemanticReviewResult parseGateResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            boolean reviewApplied = true;
            boolean fallback = false;
            String gateStatusStr = root.path("gateStatus").asText("BLOCKED");
            boolean coveragePassed = "PASS".equalsIgnoreCase(gateStatusStr);

            List<String> missingInfo = parseStringList(root.path("missingInfo"));
            List<String> misunderstandings = new ArrayList<>();
            List<String> riskBlindSpots = parseStringList(root.path("risksMissed"));
            List<String> suggestedQuestions = parseStringList(root.path("questions"));
            List<String> feedbackItems = new ArrayList<>();
            if (root.has("reason")) feedbackItems.add(root.path("reason").asText(""));
            if (root.has("developerSummary")) feedbackItems.add(root.path("developerSummary").asText(""));
            String fallbackReason = null;

            return new GateSemanticReviewResult(
                    reviewApplied, fallback, coveragePassed,
                    missingInfo, misunderstandings, riskBlindSpots,
                    suggestedQuestions, feedbackItems, fallbackReason);
        } catch (Exception e) {
            log.error("Failed to parse gate LLM response", e);
            return new GateSemanticReviewResult(
                    true, true, false,
                    List.of(), List.of(), List.of(),
                    List.of(), List.of("LLM response parse failed: " + e.getMessage()),
                    "LLM response parsing failed");
        }
    }

    private GateSemanticReviewResult fallbackReview(TaskExecutionContext context) {
        DeveloperUnderstanding dev = context.getDeveloperUnderstanding();
        boolean hasInput = dev != null
                && dev.getStageSummary() != null && !dev.getStageSummary().isBlank()
                && dev.getRiskSummary() != null && !dev.getRiskSummary().isBlank();
        return new GateSemanticReviewResult(
                false, true, hasInput,
                List.of(), List.of(), List.of(),
                hasInput ? List.of() : List.of("please supplement your understanding of current stage design and risk assessment"),
                List.of("LLM call failed, using basic rule check"),
                "LLM API unavailable");
    }

    private List<String> parseStringList(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                if (!item.isNull()) values.add(item.asText(""));
            }
        }
        return values;
    }
}
