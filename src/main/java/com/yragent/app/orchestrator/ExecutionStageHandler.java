package com.yragent.app.orchestrator;

import com.yragent.domain.execution.ExecutionPlan;
import com.yragent.domain.execution.ExecutionPlanSerializer;
import com.yragent.domain.execution.ExecutionResult;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.stage.StageResult;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import com.yragent.domain.tool.ToolCapability;
import com.yragent.domain.tool.ToolExecutor;
import com.yragent.domain.tool.ToolExecutor.ToolCall;
import com.yragent.domain.tool.ToolExecutor.ToolExecutionResult;
import com.yragent.domain.tool.ToolRegistry;
import com.yragent.domain.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExecutionStageHandler implements StageHandler {

    private static final Logger log = LoggerFactory.getLogger(ExecutionStageHandler.class);

    private final TraceRecorder traceRecorder;
    private final LlmClient llmClient;
    private final ToolExecutor toolExecutor;
    private final ExecutionPlanSerializer planSerializer;
    private final ToolRegistry toolRegistry;

    public ExecutionStageHandler(TraceRecorder traceRecorder,
                                 LlmClient llmClient,
                                 ToolExecutor toolExecutor,
                                 ExecutionPlanSerializer planSerializer,
                                 ToolRegistry toolRegistry) {
        this.traceRecorder = traceRecorder;
        this.llmClient = llmClient;
        this.toolExecutor = toolExecutor;
        this.planSerializer = planSerializer;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public StageType support() {
        return StageType.EXECUTION;
    }

    @Override
    public StageResult handle(TaskExecutionContext context) {
        traceRecorder.recordStageStart(context.getTaskId(), support());

        // 构建执行计划提示词。
        String prompt = buildExecutionPrompt(context);
        log.info("执行阶段：请求 LLM 生成执行计划");

        ExecutionPlan plan;
        try {
            String llmResponse = llmClient.chatCompletion(prompt);
            plan = planSerializer.deserializeFromLlmOutput(llmResponse);
        } catch (Exception e) {
            log.warn("LLM 执行计划生成失败，使用空计划", e);
            plan = new ExecutionPlan(List.of(), "LLM 调用失败，跳过执行");
        }
        context.setExecutionPlan(plan);

        // 执行每一步。
        List<ToolExecutionResult> stepResults = new ArrayList<>();
        int completed = 0;
        int failed = 0;
        StringBuilder outputSummary = new StringBuilder();

        for (ExecutionPlan.ExecutionStep step : plan.getSteps()) {
            log.info("执行步骤 {}: tool={}", step.index(), step.tool());
            ToolCall call = new ToolCall(step.tool(), step.params());
            ToolExecutionResult result = toolExecutor.execute(call);
            stepResults.add(result);

            if (result.success()) {
                completed++;
                outputSummary.append(String.format("[步骤%d] %s: 成功\n%s\n",
                        step.index(), step.tool(), result.output()));
            } else {
                failed++;
                outputSummary.append(String.format("[步骤%d] %s: 失败 - %s\n",
                        step.index(), step.tool(), result.error()));
            }
        }

        ExecutionResult executionResult = new ExecutionResult(
                plan, stepResults, completed, failed, outputSummary.toString());
        context.setExecutionResult(executionResult);

        context.addStageNote(String.format("EXECUTION: %d/%d 步骤成功, %d 失败",
                completed, plan.getSteps().size(), failed));

        boolean allStepsSucceeded = executionResult.allStepsSucceeded();
        String summary = String.format("execution completed: %d steps, %d succeeded, %d failed",
                plan.getSteps().size(), completed, failed);

        // 始终不阻断流水线，失败信息传递给后续 VERIFICATION/REVIEW
        StageResult result = new StageResult(
                support(), true, summary,
                summary, null, List.of(),
                allStepsSucceeded ? "全部执行步骤成功" : "存在失败步骤，详见 VERIFICATION 阶段",
                allStepsSucceeded ? null : "部分执行步骤失败"
        );
        traceRecorder.recordStageFinish(context.getTaskId(), support(), result.isPassed(), result.getSummary());
        return result;
    }

    private String buildExecutionPrompt(TaskExecutionContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个任务执行规划器。根据以下信息生成执行计划。\n\n");
        sb.append("用户需求: ").append(context.getUserInput()).append("\n\n");

        sb.append("阶段记录:\n");
        for (String note : context.getStageNotes()) {
            sb.append("- ").append(note).append("\n");
        }

        var goalAnalysis = context.getGoalAnalysis();
        if (goalAnalysis != null) {
            sb.append("\n目标分析:\n");
            sb.append("- 任务类型: ").append(goalAnalysis.taskType()).append("\n");
            sb.append("- 目标: ").append(goalAnalysis.goals()).append("\n");
            sb.append("- 约束: ").append(goalAnalysis.constraints()).append("\n");
            sb.append("- 成功标准: ").append(goalAnalysis.successCriteria()).append("\n");
        }

        var approachPlan = context.getApproachPlan();
        if (approachPlan != null) {
            sb.append("\n方法建议:\n");
            sb.append("- 方法: ").append(approachPlan.approach()).append("\n");
            sb.append("- 推荐工具: ").append(approachPlan.recommendedTools()).append("\n");
            sb.append("- 预期风险: ").append(approachPlan.risks()).append("\n");
            sb.append("- 复杂度: ").append(approachPlan.estimatedComplexity()).append("\n");
        }

        var policy = context.getProjectPolicy();
        if (policy != null) {
            sb.append("\n项目策略: projectType=").append(policy.getProjectType())
                    .append(", allowNetworkAccess=").append(policy.isAllowNetworkAccess())
                    .append("\n");
        }

        // 动态生成工具列表（含必需参数提示）。
        List<ToolCapability> tools = toolRegistry.listAll();
        sb.append("\n可用工具:\n");
        for (ToolCapability tool : tools) {
            sb.append("- ").append(tool.getName()).append(": ").append(tool.getDescription());
            String paramsHint = getParamsHint(tool.getName());
            if (!paramsHint.isEmpty()) {
                sb.append("（必需参数: ").append(paramsHint).append("）");
            }
            sb.append("\n");
        }
        String wsRoot = toolExecutor.getWorkspaceRoot();
        sb.append("\n工作目录: ").append(wsRoot).append(" (所有文件路径相对于此目录，也可以使用此目录下的绝对路径)\n\n");
        sb.append("请生成 JSON 格式的执行计划。每个步骤包含 index(从1开始)、tool、params(参数对象)、reason(执行理由)。\n");
        sb.append("整体包含 rationale(执行策略说明) 和 steps(步骤数组)。\n");
        sb.append("只输出 JSON，不要有其他内容。\n");

        return sb.toString();
    }

    private String getParamsHint(String toolName) {
        return switch (toolName) {
            case "read_file" -> "path";
            case "write_file" -> "path, content";
            case "list_dir" -> "path（可选，默认 .）";
            case "run_command" -> "command（可选 workingDir）";
            default -> "";
        };
    }
}
