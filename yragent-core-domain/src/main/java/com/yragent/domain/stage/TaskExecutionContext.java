package com.yragent.domain.stage;

import com.yragent.domain.execution.ExecutionPlan;
import com.yragent.domain.execution.ExecutionResult;
import com.yragent.domain.goal.GoalAnalysis;
import com.yragent.domain.goal.GoalClarification;
import com.yragent.domain.planning.ApproachPlan;
import com.yragent.domain.planning.PlanDocument;
import com.yragent.domain.gate.DeveloperUnderstanding;
import com.yragent.domain.gate.GateReviewAttempt;
import com.yragent.domain.gate.GateReviewNote;
import com.yragent.domain.gate.PendingDecision;
import com.yragent.domain.memory.ProjectPolicy;
import com.yragent.domain.memory.UserPreference;
import com.yragent.domain.tool.ToolSelectionDecision;
import com.yragent.domain.verification.VerificationResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TaskExecutionContext {

    private String taskId;
    private String userInput;
    private StageType currentStage;
    private final List<String> stageNotes = new ArrayList<>();
    // 这里存放当前阶段阻断时需要开发者处理的确认项。
    private final List<PendingDecision> pendingDecisions = new ArrayList<>();
    // 这里记录开发者已确认的决策编码，供门禁重新判断是否放行。
    private final Set<String> confirmedDecisionCodes = new LinkedHashSet<>();
    // 这里保存开发者对当前阶段的复述和风险判断，供认知门禁复核。
    private DeveloperUnderstanding developerUnderstanding;
    // 这里保存最新一轮门禁评审意见，便于 CLI 直接展示缺口和偏差。
    private GateReviewNote gateReviewNote;
    // 门禁尝试历史，每次门禁评审都记录为一个 attempt，保存完整快照。
    private final List<GateReviewAttempt> gateReviewAttempts = new ArrayList<>();
    // 标记是否已从 SQLite 加载门禁历史，防止重复加载。
    private boolean gateHistoryLoaded = false;
    // 开发者偏好，由 GOAL_DEFINITION 阶段加载或创建。
    private UserPreference userPreference;
    // 项目策略，由 GOAL_DEFINITION 阶段加载或创建。
    private ProjectPolicy projectPolicy;
    // 工具选择决策，由 PLANNING 阶段生成。
    private ToolSelectionDecision toolSelectionDecision;
    // LLM 生成的执行计划，由 EXECUTION 阶段生成。
    private ExecutionPlan executionPlan;
    // 执行结果汇总，由 EXECUTION 阶段产出。
    private ExecutionResult executionResult;
    // 验证结果，由 VERIFICATION 阶段产出。
    private VerificationResult verificationResult;
    // LLM 目标分析结果，由 GOAL_DEFINITION 阶段产出。
    private GoalAnalysis goalAnalysis;
    // LLM 高层规划结果，由 PLANNING 阶段产出。
    private ApproachPlan approachPlan;
    private String currentStageSummary;
    private String nextAction;
    private String failureReason;
    // 目标澄清交互数据，由 CLARIFY_GOAL 阶段产出。
    private GoalClarification goalClarification;
    // 详细计划文档，由 PLANNING 阶段产出（V2 替代 ApproachPlan）。
    private PlanDocument planDocument;
    private int currentRound = 0;
    private final List<RoundRecord> roundHistory = new ArrayList<>();
    private boolean completed = false;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getUserInput() {
        return userInput;
    }

    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }

    public StageType getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(StageType currentStage) {
        this.currentStage = currentStage;
    }

    public List<String> getStageNotes() {
        return stageNotes;
    }

    public void addStageNote(String note) {
        this.stageNotes.add(note);
    }

    public List<PendingDecision> getPendingDecisions() {
        return pendingDecisions;
    }

    public void replacePendingDecisions(List<PendingDecision> decisions) {
        // 每个阶段结果都会覆盖当前待确认项，避免旧结果污染新阶段。
        this.pendingDecisions.clear();
        this.pendingDecisions.addAll(decisions);
    }

    public Set<String> getConfirmedDecisionCodes() {
        return confirmedDecisionCodes;
    }

    public void confirmDecision(String decisionCode) {
        this.confirmedDecisionCodes.add(decisionCode);
    }

    public boolean hasConfirmedDecision(String decisionCode) {
        return this.confirmedDecisionCodes.contains(decisionCode);
    }

    public DeveloperUnderstanding getDeveloperUnderstanding() {
        return developerUnderstanding;
    }

    public void setDeveloperUnderstanding(DeveloperUnderstanding developerUnderstanding) {
        this.developerUnderstanding = developerUnderstanding;
    }

    public GateReviewNote getGateReviewNote() {
        return gateReviewNote;
    }

    public void setGateReviewNote(GateReviewNote gateReviewNote) {
        this.gateReviewNote = gateReviewNote;
    }

    public String getCurrentStageSummary() {
        return currentStageSummary;
    }

    public void setCurrentStageSummary(String currentStageSummary) {
        this.currentStageSummary = currentStageSummary;
    }

    public String getNextAction() {
        return nextAction;
    }

    public void setNextAction(String nextAction) {
        this.nextAction = nextAction;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public List<GateReviewAttempt> getGateReviewAttempts() {
        return gateReviewAttempts;
    }

    public void addGateReviewAttempt(GateReviewAttempt attempt) {
        this.gateReviewAttempts.add(attempt);
    }

    public boolean isGateHistoryLoaded() {
        return gateHistoryLoaded;
    }

    public void setGateHistoryLoaded(boolean gateHistoryLoaded) {
        this.gateHistoryLoaded = gateHistoryLoaded;
    }

    public UserPreference getUserPreference() {
        return userPreference;
    }

    public void setUserPreference(UserPreference userPreference) {
        this.userPreference = userPreference;
    }

    public ProjectPolicy getProjectPolicy() {
        return projectPolicy;
    }

    public void setProjectPolicy(ProjectPolicy projectPolicy) {
        this.projectPolicy = projectPolicy;
    }

    public ToolSelectionDecision getToolSelectionDecision() {
        return toolSelectionDecision;
    }

    public void setToolSelectionDecision(ToolSelectionDecision toolSelectionDecision) {
        this.toolSelectionDecision = toolSelectionDecision;
    }

    public ExecutionPlan getExecutionPlan() {
        return executionPlan;
    }

    public void setExecutionPlan(ExecutionPlan executionPlan) {
        this.executionPlan = executionPlan;
    }

    public ExecutionResult getExecutionResult() {
        return executionResult;
    }

    public void setExecutionResult(ExecutionResult executionResult) {
        this.executionResult = executionResult;
    }

    public VerificationResult getVerificationResult() {
        return verificationResult;
    }

    public void setVerificationResult(VerificationResult verificationResult) {
        this.verificationResult = verificationResult;
    }

    public GoalAnalysis getGoalAnalysis() {
        return goalAnalysis;
    }

    public void setGoalAnalysis(GoalAnalysis goalAnalysis) {
        this.goalAnalysis = goalAnalysis;
    }

    public ApproachPlan getApproachPlan() {
        return approachPlan;
    }

    public void setApproachPlan(ApproachPlan approachPlan) {
        this.approachPlan = approachPlan;
    }

    public GoalClarification getGoalClarification() {
        return goalClarification;
    }

    public void setGoalClarification(GoalClarification goalClarification) {
        this.goalClarification = goalClarification;
    }

    public PlanDocument getPlanDocument() {
        return planDocument;
    }

    public void setPlanDocument(PlanDocument planDocument) {
        this.planDocument = planDocument;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public List<RoundRecord> getRoundHistory() {
        return roundHistory;
    }

    public void addRoundRecord(RoundRecord record) {
        this.roundHistory.add(record);
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}
