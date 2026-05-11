package com.yragent.orchestrator;

import com.yragent.domain.goal.GoalAnalysis;
import com.yragent.domain.memory.MemoryFragment;
import com.yragent.domain.memory.MemoryService;
import com.yragent.domain.memory.ProjectPolicy;
import com.yragent.domain.memory.UserPreference;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.stage.StageResult;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import com.yragent.domain.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GoalDefinitionStageHandler implements StageHandler {

    private static final Logger log = LoggerFactory.getLogger(GoalDefinitionStageHandler.class);

    private final MemoryService memoryService;
    private final TraceRecorder traceRecorder;
    private final LlmClient llmClient;

    public GoalDefinitionStageHandler(MemoryService memoryService,
                                       TraceRecorder traceRecorder,
                                       LlmClient llmClient) {
        this.memoryService = memoryService;
        this.traceRecorder = traceRecorder;
        this.llmClient = llmClient;
    }

    @Override
    public StageType support() {
        return StageType.GOAL_DEFINITION;
    }

    @Override
    public StageResult handle(TaskExecutionContext context) {
        traceRecorder.recordStageStart(context.getTaskId(), support());
        List<MemoryFragment> memories = memoryService.loadForStage(support(), context);

        // 加载或创建开发者偏好。
        UserPreference preference = memoryService.loadPreference();
        if (memories.stream().noneMatch(m -> m.getType() == com.yragent.domain.memory.MemoryType.USER_PREFERENCE)) {
            // 库中无记录，保存默认偏好供后续任务复用。
            memoryService.saveOrUpdatePreference(preference);
        }
        context.setUserPreference(preference);

        // 加载或创建项目策略。
        ProjectPolicy policy = memoryService.loadPolicy();
        if (memories.stream().noneMatch(m -> m.getType() == com.yragent.domain.memory.MemoryType.PROJECT_POLICY)) {
            // 库中无记录，保存默认策略供后续任务复用。
            memoryService.saveOrUpdatePolicy(policy);
        }
        context.setProjectPolicy(policy);

        // LLM 目标分析。
        GoalAnalysis goalAnalysis;
        try {
            String prompt = buildGoalAnalysisPrompt(context.getUserInput(), preference, policy);
            String llmResponse = llmClient.chatCompletion(prompt);
            goalAnalysis = parseGoalAnalysisResponse(llmResponse);
        } catch (Exception e) {
            log.warn("LLM 目标分析失败，使用空分析", e);
            goalAnalysis = GoalAnalysis.empty();
        }
        context.setGoalAnalysis(goalAnalysis);
        context.addStageNote(String.format("GOAL_ANALYSIS: taskType=%s, goals=%d, constraints=%d, confidence=%s, needsClarification=%s",
                goalAnalysis.taskType(), goalAnalysis.goals().size(), goalAnalysis.constraints().size(),
                goalAnalysis.confidence(), goalAnalysis.needsClarification()));

        String summary = String.format(
                "goal defined: riskTolerance=%s, projectType=%s, %d memory fragments loaded",
                preference.getRiskTolerance(),
                policy.getProjectType(),
                memories.size()
        );
        StageResult result = new StageResult(support(), true, summary);
        traceRecorder.recordStageFinish(context.getTaskId(), support(), result.isPassed(), result.getSummary());
        return result;
    }

    private String buildGoalAnalysisPrompt(String userInput, UserPreference preference, ProjectPolicy policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个任务目标分析器。分析用户请求，提取结构化信息。\n\n");
        sb.append("用户请求: ").append(userInput).append("\n\n");
        sb.append("项目背景: projectType=").append(policy.getProjectType())
                .append(", allowNetworkAccess=").append(policy.isAllowNetworkAccess())
                .append(", riskTolerance=").append(preference.getRiskTolerance()).append("\n\n");
        sb.append("请分析并返回 JSON:\n");
        sb.append("1. taskType: 任务类型 (file_operation / code_generation / data_query / config_change / other)\n");
        sb.append("2. goals: 具体目标列表\n");
        sb.append("3. constraints: 约束条件列表（含用户请求中的限制 + 项目策略约束）\n");
        sb.append("4. successCriteria: 成功标准列表（可验证的、具体的标准）\n");
        sb.append("5. missingInfo: 需要补充的信息列表（空数组表示信息充足）\n");
        sb.append("6. confidence: 置信度 (high / medium / low)\n");
        sb.append("7. needsClarification: 是否需要澄清 (true / false)\n\n");
        sb.append("JSON 格式: {\"taskType\":\"...\", \"goals\":[\"...\"], \"constraints\":[\"...\"], \"successCriteria\":[\"...\"], \"missingInfo\":[\"...\"], \"confidence\":\"medium\", \"needsClarification\":false}\n");
        sb.append("只输出 JSON，不要有其他内容。\n");
        return sb.toString();
    }

    private GoalAnalysis parseGoalAnalysisResponse(String llmResponse) {
        String json = llmResponse.trim();
        int braceStart = json.indexOf('{');
        int braceEnd = json.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            json = json.substring(braceStart, braceEnd + 1);
        }

        String taskType = extractString(json, "taskType");
        if (taskType.isBlank()) taskType = "other";

        List<String> goals = extractArray(json, "goals");
        List<String> constraints = extractArray(json, "constraints");
        List<String> successCriteria = extractArray(json, "successCriteria");
        List<String> missingInfo = extractArray(json, "missingInfo");
        String confidence = extractString(json, "confidence");
        if (confidence.isBlank()) confidence = "medium";
        boolean needsClarification = extractBoolean(json, "needsClarification");

        return new GoalAnalysis(taskType, goals, constraints, successCriteria,
                missingInfo, confidence, needsClarification);
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

    private boolean extractBoolean(String json, String key) {
        int keyStart = json.indexOf('"' + key + '"');
        if (keyStart < 0) return false;
        int colonPos = json.indexOf(':', keyStart);
        if (colonPos < 0) return false;
        String afterColon = json.substring(colonPos + 1).trim();
        return afterColon.startsWith("true");
    }
}
