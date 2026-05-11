package com.yragent.orchestrator;

import com.yragent.domain.model.LlmClient;
import com.yragent.domain.planning.PlanDocument;
import com.yragent.domain.stage.RoundRecord;
import com.yragent.domain.stage.StageResult;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import com.yragent.domain.trace.TraceRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ReviewStageHandler implements StageHandler {

    private static final Logger log = LoggerFactory.getLogger(ReviewStageHandler.class);

    private final TraceRecorder traceRecorder;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ReviewStageHandler(TraceRecorder traceRecorder, LlmClient llmClient) {
        this.traceRecorder = traceRecorder;
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public StageType support() {
        return StageType.REVIEW;
    }

    @Override
    public StageResult handle(TaskExecutionContext context) {
        traceRecorder.recordStageStart(context.getTaskId(), support());

        // 构建结构化审查提示词，聚合所有阶段信息
        String prompt = buildReviewPrompt(context);
        log.info("审查阶段：请求 LLM 生成结构化审查（第{}轮）", context.getCurrentRound());

        String reviewSummary;
        boolean projectComplete = false;
        List<String> nextRoundFocus = List.of();
        try {
            String llmResponse = llmClient.structuredCompletion(prompt, REVIEW_RESPONSE_SCHEMA);
            llmResponse = cleanResponse(llmResponse);
            log.debug("LLM 审查原始响应: {}", llmResponse);

            // 解析结构化 JSON
            JsonNode root = objectMapper.readTree(llmResponse);
            reviewSummary = root.path("roundSummary").asText("审查摘要生成失败");
            projectComplete = root.path("projectComplete").asBoolean(false);
            nextRoundFocus = parseStringList(root.path("nextRoundFocus"));

            String whatWentWell = String.join("; ", parseStringList(root.path("whatWentWell")));
            String whatWentWrong = String.join("; ", parseStringList(root.path("whatWentWrong")));
            String overallAssessment = root.path("overallAssessment").asText("");

            // 丰富摘要
            StringBuilder enriched = new StringBuilder(reviewSummary);
            if (!overallAssessment.isBlank()) {
                enriched.append(" [评价: ").append(overallAssessment).append("]");
            }
            reviewSummary = enriched.toString();

            log.info("LLM 审查结果: projectComplete={}, review={}", projectComplete,
                    reviewSummary.substring(0, Math.min(100, reviewSummary.length())));
        } catch (Exception e) {
            log.warn("LLM 审查调用失败", e);
            reviewSummary = "审查摘要生成失败: " + e.getMessage();
            projectComplete = false;
        }

        // 回写上下文
        context.setCompleted(projectComplete);
        context.addStageNote("REVIEW[round=" + context.getCurrentRound() + "]: " + reviewSummary);

        // 保存 RoundRecord
        RoundRecord roundRecord = new RoundRecord(
                context.getCurrentRound(),
                context.getPlanDocument(),
                context.getExecutionResult(),
                context.getVerificationResult(),
                reviewSummary,
                projectComplete
        );
        context.addRoundRecord(roundRecord);

        String resultSummary = String.format(
                "review[round=%d]: complete=%s, summary=%s",
                context.getCurrentRound(), projectComplete,
                reviewSummary.length() > 80 ? reviewSummary.substring(0, 80) + "..." : reviewSummary);
        context.setCurrentStageSummary(reviewSummary);

        StageResult result = new StageResult(support(), true, resultSummary,
                reviewSummary, null, List.of(),
                projectComplete ? "PROJECT_COMPLETE" : "NEXT_ROUND: " + nextRoundFocus,
                null);
        traceRecorder.recordStageFinish(context.getTaskId(), support(), true, result.getSummary());
        return result;
    }

    private String buildReviewPrompt(TaskExecutionContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个项目审查器。审查当前轮次的完整执行轨迹，判断项目是否完成。\n\n");
        sb.append("用户原始需求: ").append(context.getUserInput()).append("\n\n");

        // 当前轮次
        sb.append("当前轮次: 第").append(context.getCurrentRound()).append("轮\n\n");

        // 计划文档
        PlanDocument planDocument = context.getPlanDocument();
        if (planDocument != null) {
            sb.append("=== 计划文档 ===\n");
            sb.append("概述: ").append(planDocument.getOverview()).append("\n");
            sb.append("架构: ").append(planDocument.getArchitecture()).append("\n");
            sb.append("复杂度: ").append(planDocument.getEstimatedComplexity()).append("\n");
            if (!planDocument.getSteps().isEmpty()) {
                sb.append("步骤数: ").append(planDocument.getSteps().size()).append("\n");
            }
            if (!planDocument.getRisks().isEmpty()) {
                sb.append("风险: ").append(String.join(", ", planDocument.getRisks())).append("\n");
            }
            sb.append("\n");
        }

        // 阶段记录
        sb.append("=== 阶段记录 ===\n");
        for (String note : context.getStageNotes()) {
            sb.append("- ").append(note).append("\n");
        }

        // 门禁历史
        var gateAttempts = context.getGateReviewAttempts();
        if (!gateAttempts.isEmpty()) {
            sb.append("\n=== 门禁历史 ===\n");
            for (var attempt : gateAttempts) {
                sb.append(String.format("- 第%d轮: %s (%s)\n",
                        attempt.getAttemptIndex(),
                        attempt.getFinalStatus(),
                        attempt.getTimestamp()));
            }
        }

        // 执行结果
        var executionResult = context.getExecutionResult();
        if (executionResult != null) {
            sb.append("\n=== 执行结果 ===\n");
            sb.append("完成步骤: ").append(executionResult.getCompletedSteps())
                    .append(", 失败步骤: ").append(executionResult.getFailedSteps()).append("\n");
            sb.append("输出摘要: ").append(executionResult.getOutputSummary()).append("\n");
        }

        // 验证结果
        var verificationResult = context.getVerificationResult();
        if (verificationResult != null) {
            sb.append("\n=== 验证结论 ===\n");
            sb.append("通过: ").append(verificationResult.isPassed()).append("\n");
            sb.append("摘要: ").append(verificationResult.getSummary()).append("\n");
            if (!verificationResult.getIssues().isEmpty()) {
                sb.append("问题:\n");
                for (String issue : verificationResult.getIssues()) {
                    sb.append("- ").append(issue).append("\n");
                }
            }
        }

        sb.append("\n请输出严格 JSON（不要包含其他文字），格式如下：\n");
        sb.append("{\n");
        sb.append("  \"roundSummary\": \"本轮执行摘要（中文，200字以内）\",\n");
        sb.append("  \"whatWentWell\": [\"做得好的方面\"],\n");
        sb.append("  \"whatWentWrong\": [\"需要改进的方面\"],\n");
        sb.append("  \"projectComplete\": true/false,\n");
        sb.append("  \"nextRoundFocus\": [\"下一轮应关注的重点领域\"],\n");
        sb.append("  \"overallAssessment\": \"总体评价（一句话）\"\n");
        sb.append("}\n");
        sb.append("判断 projectComplete 为 true 的条件：所有步骤执行成功、验证通过、输出满足用户需求。\n");
        sb.append("否则 projectComplete 为 false，并在 nextRoundFocus 中给出下一轮改进方向。\n");

        return sb.toString();
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> items = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (!item.isNull()) {
                    items.add(item.asText(""));
                }
            }
        }
        return items;
    }

    private String cleanResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        // 去掉 markdown 代码块包裹。
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end).trim();
            }
        }
        return trimmed;
    }

    private static final String REVIEW_RESPONSE_SCHEMA = """
            {"type":"object","properties":{"roundSummary":{"type":"string"},"whatWentWell":{"type":"array","items":{"type":"string"}},"whatWentWrong":{"type":"array","items":{"type":"string"}},"projectComplete":{"type":"boolean"},"nextRoundFocus":{"type":"array","items":{"type":"string"}},"overallAssessment":{"type":"string"}},"required":["roundSummary","whatWentWell","whatWentWrong","projectComplete","nextRoundFocus","overallAssessment"]}""";
}
