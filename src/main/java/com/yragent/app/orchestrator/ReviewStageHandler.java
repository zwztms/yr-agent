package com.yragent.app.orchestrator;

import com.yragent.domain.model.LlmClient;
import com.yragent.domain.stage.StageResult;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import com.yragent.domain.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReviewStageHandler implements StageHandler {

    private static final Logger log = LoggerFactory.getLogger(ReviewStageHandler.class);

    private final TraceRecorder traceRecorder;
    private final LlmClient llmClient;

    public ReviewStageHandler(TraceRecorder traceRecorder, LlmClient llmClient) {
        this.traceRecorder = traceRecorder;
        this.llmClient = llmClient;
    }

    @Override
    public StageType support() {
        return StageType.REVIEW;
    }

    @Override
    public StageResult handle(TaskExecutionContext context) {
        traceRecorder.recordStageStart(context.getTaskId(), support());

        // 构建审查提示词，聚合所有阶段信息。
        String prompt = buildReviewPrompt(context);
        log.info("审查阶段：请求 LLM 生成审查摘要");

        String reviewSummary;
        try {
            reviewSummary = llmClient.chatCompletion(prompt);
            // 清理 markdown 代码块包裹。
            reviewSummary = cleanResponse(reviewSummary);
        } catch (Exception e) {
            log.warn("LLM 审查调用失败", e);
            reviewSummary = "审查摘要生成失败: " + e.getMessage();
        }

        context.addStageNote("REVIEW: " + reviewSummary);
        StageResult result = new StageResult(support(), true,
                "review bundle generated, length=" + reviewSummary.length());
        traceRecorder.recordStageFinish(context.getTaskId(), support(), true, result.getSummary());
        return result;
    }

    private String buildReviewPrompt(TaskExecutionContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个任务审查器。为以下任务的完整执行轨迹生成审查摘要。\n\n");
        sb.append("用户需求: ").append(context.getUserInput()).append("\n\n");

        sb.append("=== 阶段记录 ===\n");
        for (String note : context.getStageNotes()) {
            sb.append("- ").append(note).append("\n");
        }

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

        var executionResult = context.getExecutionResult();
        if (executionResult != null) {
            sb.append("\n=== 执行结果 ===\n");
            sb.append(executionResult.getOutputSummary());
        }

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

        sb.append("\n请生成结构化审查摘要，包含:\n");
        sb.append("1. 任务完成情况概述\n");
        sb.append("2. 门禁通过情况\n");
        sb.append("3. 执行质量评价\n");
        sb.append("4. 建议和注意事项\n");
        sb.append("用中文回复，控制在 300 字以内。\n");

        return sb.toString();
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
}
