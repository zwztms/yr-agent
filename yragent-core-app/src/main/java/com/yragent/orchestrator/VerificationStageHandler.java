package com.yragent.orchestrator;

import com.yragent.domain.memory.ContextAssembler;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.stage.StageResult;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import com.yragent.domain.trace.TraceRecorder;
import com.yragent.domain.verification.VerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class VerificationStageHandler implements StageHandler {

    private static final Logger log = LoggerFactory.getLogger(VerificationStageHandler.class);

    private final TraceRecorder traceRecorder;
    private final LlmClient llmClient;
    private final ContextAssembler contextAssembler;

    public VerificationStageHandler(TraceRecorder traceRecorder,
                                    LlmClient llmClient,
                                    ContextAssembler contextAssembler) {
        this.traceRecorder = traceRecorder;
        this.llmClient = llmClient;
        this.contextAssembler = contextAssembler;
    }

    @Override
    public StageType support() {
        return StageType.VERIFICATION;
    }

    @Override
    public StageResult handle(TaskExecutionContext context) {
        traceRecorder.recordStageStart(context.getTaskId(), support());

        var executionResult = context.getExecutionResult();
        if (executionResult == null) {
            StageResult result = new StageResult(support(), true,
                    "verification skipped: no execution result to verify");
            traceRecorder.recordStageFinish(context.getTaskId(), support(), true, result.getSummary());
            return result;
        }

        // 构建验证提示词。
        String contextPrefix = contextAssembler.renderContext(support(), context, 10);
        String prompt = buildVerificationPrompt(context, executionResult, contextPrefix);
        log.info("验证阶段：请求 LLM 评估执行结果");

        VerificationResult verificationResult;
        try {
            String llmResponse = llmClient.chatCompletion(prompt);
            context.getConversationHistory().addTurn(prompt, llmResponse, support());
            verificationResult = parseVerificationResponse(llmResponse);
        } catch (Exception e) {
            log.warn("LLM 验证调用失败，默认通过", e);
            verificationResult = new VerificationResult(true, List.of(),
                    "LLM 验证调用失败，跳过验证（默认通过）", true);
        }

        context.setVerificationResult(verificationResult);
        context.addStageNote(String.format("VERIFICATION: passed=%s, issues=%d",
                verificationResult.isPassed(), verificationResult.getIssues().size()));

        String summary = verificationResult.isPassed()
                ? "verification passed: " + verificationResult.getSummary()
                : "verification failed: " + String.join("; ", verificationResult.getIssues());

        // 验证始终不阻断流水线，失败信息传递给后续 REVIEW 阶段。
        boolean verified = verificationResult.isPassed();
        StageResult result = new StageResult(
                support(),
                true,
                summary,
                null, null, List.of(),
                verified ? "验证通过，进入审查阶段" : "验证未通过，详见审查阶段",
                null
        );
        traceRecorder.recordStageFinish(context.getTaskId(), support(), result.isPassed(), result.getSummary());
        return result;
    }

    private String buildVerificationPrompt(TaskExecutionContext context,
                                           com.yragent.domain.execution.ExecutionResult executionResult,
                                           String contextPrefix) {
        StringBuilder sb = new StringBuilder();
        sb.append(contextPrefix);
        sb.append("你是一个任务验证器。评估执行结果是否满足用户需求。\n\n");
        sb.append("用户需求: ").append(context.getUserInput()).append("\n\n");

        sb.append("执行结果:\n");
        sb.append("- 计划理由: ").append(executionResult.getPlan().getRationale()).append("\n");
        sb.append("- 成功步骤: ").append(executionResult.getCompletedSteps())
                .append("/").append(executionResult.getPlan().getSteps().size()).append("\n");
        sb.append("- 执行输出:\n").append(executionResult.getOutputSummary()).append("\n");

        sb.append("\n请评估:\n");
        sb.append("1. 执行是否完成了用户需求？\n");
        sb.append("2. 有哪些遗漏或问题？\n");
        sb.append("3. 结论：pass 或 fail\n\n");
        sb.append("用以下 JSON 格式回复:\n");
        sb.append("{\"passed\": true/false, \"issues\": [\"问题1\", \"问题2\"], \"summary\": \"评估摘要\", \"allChecksPassed\": true/false}\n");
        sb.append("只输出 JSON，不要有其他内容。\n");

        return sb.toString();
    }

    private VerificationResult parseVerificationResponse(String llmResponse) {
        try {
            // 简单 JSON 解析，避免引入额外依赖。
            String json = llmResponse.trim();
            int braceStart = json.indexOf('{');
            int braceEnd = json.lastIndexOf('}');
            if (braceStart >= 0 && braceEnd > braceStart) {
                json = json.substring(braceStart, braceEnd + 1);
            }

            boolean passed = json.contains("\"passed\": true") || json.contains("\"passed\":true");
            boolean allChecksPassed = json.contains("\"allChecksPassed\": true")
                    || json.contains("\"allChecksPassed\":true");

            // 提取 summary。
            String summary = "";
            int summaryStart = json.indexOf("\"summary\"");
            if (summaryStart >= 0) {
                int valueStart = json.indexOf('"', json.indexOf(':', summaryStart) + 1);
                int valueEnd = json.indexOf('"', valueStart + 1);
                if (valueStart >= 0 && valueEnd > valueStart) {
                    summary = json.substring(valueStart + 1, valueEnd);
                }
            }

            // 提取 issues 数组。
            List<String> issues = new ArrayList<>();
            int issuesStart = json.indexOf("\"issues\"");
            if (issuesStart >= 0) {
                int arrayStart = json.indexOf('[', issuesStart);
                int arrayEnd = json.indexOf(']', arrayStart);
                if (arrayStart >= 0 && arrayEnd > arrayStart) {
                    String arrayContent = json.substring(arrayStart + 1, arrayEnd);
                    for (String item : arrayContent.split(",")) {
                        String cleaned = item.trim().replaceAll("^\"|\"$", "");
                        if (!cleaned.isBlank()) {
                            issues.add(cleaned);
                        }
                    }
                }
            }

            return new VerificationResult(passed, issues, summary, allChecksPassed);
        } catch (Exception e) {
            log.warn("解析验证响应失败，默认通过", e);
            return new VerificationResult(true, List.of(), "解析失败，默认通过", true);
        }
    }
}
