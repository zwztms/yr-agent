package com.yragent.app.orchestrator;

import com.yragent.domain.goal.GoalAnalysis;
import com.yragent.domain.memory.MemoryService;
import com.yragent.domain.memory.ProjectPolicy;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.planning.ApproachPlan;
import com.yragent.domain.stage.StageResult;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import com.yragent.domain.tool.ToolSelectionDecision;
import com.yragent.domain.tool.ToolsetSelector;
import com.yragent.domain.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PlanningStageHandler implements StageHandler {

    private static final Logger log = LoggerFactory.getLogger(PlanningStageHandler.class);

    private final TraceRecorder traceRecorder;
    private final ToolsetSelector toolsetSelector;
    private final MemoryService memoryService;
    private final LlmClient llmClient;

    public PlanningStageHandler(TraceRecorder traceRecorder,
                                ToolsetSelector toolsetSelector,
                                MemoryService memoryService,
                                LlmClient llmClient) {
        this.traceRecorder = traceRecorder;
        this.toolsetSelector = toolsetSelector;
        this.memoryService = memoryService;
        this.llmClient = llmClient;
    }

    @Override
    public StageType support() {
        return StageType.PLANNING;
    }

    @Override
    public StageResult handle(TaskExecutionContext context) {
        traceRecorder.recordStageStart(context.getTaskId(), support());
        ToolSelectionDecision decision = toolsetSelector.selectForPlanning();

        // 将工具选择决策保存到上下文，供 EXECUTION 阶段使用。
        context.setToolSelectionDecision(decision);

        // 获取项目策略，若上下文没有则从库加载。
        ProjectPolicy policy = context.getProjectPolicy();
        if (policy == null) {
            policy = memoryService.loadPolicy();
            context.setProjectPolicy(policy);
        }

        // LLM 高层规划。
        GoalAnalysis goalAnalysis = context.getGoalAnalysis();
        ApproachPlan approachPlan;
        try {
            String prompt = buildApproachPrompt(context.getUserInput(), goalAnalysis,
                    decision.getAllowedTools(), policy);
            String llmResponse = llmClient.chatCompletion(prompt);
            approachPlan = parseApproachResponse(llmResponse);
        } catch (Exception e) {
            log.warn("LLM 规划失败，使用空规划", e);
            approachPlan = ApproachPlan.empty();
        }
        context.setApproachPlan(approachPlan);
        context.addStageNote(String.format("APPROACH: complexity=%s, risks=%d, recommendedTools=%d",
                approachPlan.estimatedComplexity(), approachPlan.risks().size(),
                approachPlan.recommendedTools().size()));

        String summary = String.format(
                "planning: tools=%d, complexity=%s, projectType=%s, networkAccess=%s",
                decision.getAllowedTools().size(),
                approachPlan.estimatedComplexity(),
                policy.getProjectType(),
                policy.isAllowNetworkAccess() ? "allowed" : "denied"
        );
        StageResult result = new StageResult(support(), true, summary);
        traceRecorder.recordStageFinish(context.getTaskId(), support(), result.isPassed(), result.getSummary());
        return result;
    }

    private String buildApproachPrompt(String userInput, GoalAnalysis goalAnalysis,
                                        List<com.yragent.domain.tool.ToolCapability> availableTools,
                                        ProjectPolicy policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个任务规划器。基于目标分析结果和方法规划。\n\n");
        sb.append("用户请求: ").append(userInput).append("\n\n");

        if (goalAnalysis != null && !goalAnalysis.equals(GoalAnalysis.empty())) {
            sb.append("目标分析:\n");
            sb.append("- 任务类型: ").append(goalAnalysis.taskType()).append("\n");
            sb.append("- 目标: ").append(goalAnalysis.goals()).append("\n");
            sb.append("- 约束: ").append(goalAnalysis.constraints()).append("\n");
            sb.append("- 成功标准: ").append(goalAnalysis.successCriteria()).append("\n\n");
        }

        sb.append("可用工具:\n");
        for (var tool : availableTools) {
            sb.append("- ").append(tool.getName()).append(": ").append(tool.getDescription())
                    .append(" (风险: ").append(tool.getRiskLevel()).append(")\n");
        }

        sb.append("\n项目约束: projectType=").append(policy.getProjectType())
                .append(", allowNetworkAccess=").append(policy.isAllowNetworkAccess()).append("\n\n");

        sb.append("请规划方法并返回 JSON:\n");
        sb.append("1. approach: 方法描述（一句话）\n");
        sb.append("2. recommendedTools: 推荐使用的工具名列表\n");
        sb.append("3. risks: 识别的风险列表\n");
        sb.append("4. estimatedComplexity: low / medium / high\n\n");
        sb.append("JSON 格式: {\"approach\":\"...\", \"recommendedTools\":[\"...\"], \"risks\":[\"...\"], \"estimatedComplexity\":\"low\"}\n");
        sb.append("只输出 JSON，不要有其他内容。\n");
        return sb.toString();
    }

    private ApproachPlan parseApproachResponse(String llmResponse) {
        String json = llmResponse.trim();
        int braceStart = json.indexOf('{');
        int braceEnd = json.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            json = json.substring(braceStart, braceEnd + 1);
        }

        String approach = extractString(json, "approach");
        List<String> recommendedTools = extractArray(json, "recommendedTools");
        List<String> risks = extractArray(json, "risks");
        String complexity = extractString(json, "estimatedComplexity");
        if (complexity.isBlank()) complexity = "low";

        return new ApproachPlan(approach, recommendedTools, risks, complexity);
    }

    private String extractString(String json, String key) {
        int keyStart = json.indexOf('"' + key + '"');
        if (keyStart < 0) return "";
        int colonPos = json.indexOf(':', keyStart);
        if (colonPos < 0) return "";
        int valueStart = json.indexOf('"', colonPos + 1);
        if (valueStart < 0) return "";
        int valueEnd = json.indexOf('"', valueStart + 1);
        if (valueEnd < 0) return "";
        return json.substring(valueStart + 1, valueEnd);
    }

    private List<String> extractArray(String json, String key) {
        List<String> items = new ArrayList<>();
        int keyStart = json.indexOf('"' + key + '"');
        if (keyStart < 0) return items;
        int bracketStart = json.indexOf('[', keyStart);
        int bracketEnd = json.indexOf(']', bracketStart);
        if (bracketStart < 0 || bracketEnd < 0) return items;
        String arrayContent = json.substring(bracketStart + 1, bracketEnd);
        for (String item : arrayContent.split(",")) {
            String cleaned = item.trim().replaceAll("^\"|\"$", "");
            if (!cleaned.isBlank()) {
                items.add(cleaned);
            }
        }
        return items;
    }
}
