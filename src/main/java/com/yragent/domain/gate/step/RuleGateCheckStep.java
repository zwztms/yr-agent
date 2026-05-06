package com.yragent.domain.gate.step;

import com.yragent.domain.gate.DeveloperUnderstanding;
import com.yragent.domain.gate.GateStatus;
import com.yragent.domain.gate.PendingDecision;
import com.yragent.domain.gate.PendingDecisionType;
import com.yragent.domain.gate.RuleGateReviewResult;
import com.yragent.domain.goal.GoalAnalysis;
import com.yragent.domain.planning.ApproachPlan;
import com.yragent.domain.stage.TaskExecutionContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class RuleGateCheckStep {

    public RuleGateReviewResult check(TaskExecutionContext context) {
        boolean goalAnalysisEmpty = isGoalAnalysisEmpty(context);
        boolean approachPlanEmpty = isApproachPlanEmpty(context);

        // 上游阶段质量验证：两个阶段都失败时直接阻断，不要求开发者对空白设计进行确认。
        if (goalAnalysisEmpty && approachPlanEmpty) {
            String stageSummary = "GOAL_DEFINITION 和 PLANNING 阶段均未产出有效结果。"
                    + "LLM 调用可能失败，请检查 API Key 和网络连接后重新提交任务。";
            return new RuleGateReviewResult(
                    GateStatus.BLOCKED,
                    stageSummary,
                    false,
                    false,
                    List.of(),
                    List.of("上游阶段未产出有效结果：GoalAnalysis 和 ApproachPlan 均为空。"
                            + "请检查 DEEPSEEK_API_KEY 环境变量和网络连接。"),
                    "GOAL_DEFINITION 和 PLANNING 均未产出有效结果"
            );
        }

        String stageSummary = buildStageSummary(context, goalAnalysisEmpty, approachPlanEmpty);
        List<PendingDecision> pendingDecisions = new ArrayList<>();
        List<String> feedbackItems = new ArrayList<>();

        // 若上游有阶段失败，反馈中提醒开发者注意。
        if (goalAnalysisEmpty) {
            feedbackItems.add("注意：GOAL_DEFINITION 阶段未产出有效分析，当前仅有 PLANNING 结果。");
        }
        if (approachPlanEmpty) {
            feedbackItems.add("注意：PLANNING 阶段未产出有效规划，当前仅有 GOAL_DEFINITION 结果。");
        }

        DeveloperUnderstanding developerUnderstanding = context.getDeveloperUnderstanding();
        if (developerUnderstanding == null || isBlank(developerUnderstanding.getStageSummary())) {
            pendingDecisions.add(new PendingDecision(
                    PendingDecisionType.UNDERSTANDING_INPUT,
                    "understanding.summary",
                    "提交当前阶段理解",
                    "请先用自己的话复述当前阶段已经形成了什么设计、边界和结论。",
                    true
            ));
            feedbackItems.add("尚未收到开发者对当前阶段的复述。");
        } else {
            collectUnderstandingFeedback(context, developerUnderstanding.getStageSummary(), feedbackItems);
        }

        if (developerUnderstanding == null || isBlank(developerUnderstanding.getRiskSummary())) {
            pendingDecisions.add(new PendingDecision(
                    PendingDecisionType.RISK_INPUT,
                    "risk.summary",
                    "提交风险判断",
                    "请明确当前阶段的主要风险、误解点或需要继续收紧的授权口径。",
                    true
            ));
            feedbackItems.add("尚未收到开发者对当前阶段风险的判断。");
        } else {
            collectRiskFeedback(developerUnderstanding.getRiskSummary(), feedbackItems);
        }

        pendingDecisions.add(new PendingDecision(
                PendingDecisionType.CONFIRMATION,
                "goal.confirm",
                "确认任务目标",
                goalAnalysisEmpty
                        ? "注意：GOAL_DEFINITION 阶段未产出有效分析，请确认任务目标是否准确。"
                        : "确认当前任务目标是否准确，避免系统按错误目标继续推进。",
                true
        ));

        // PLANNING 阶段完成后才需要确认工具范围；若规划为空则不添加此确认项。
        if (!approachPlanEmpty) {
            pendingDecisions.add(new PendingDecision(
                    PendingDecisionType.CONFIRMATION,
                    "toolset.confirm",
                    "确认候选工具范围",
                    "确认规划阶段筛出的候选工具是否符合本次任务边界。",
                    true
            ));
        }

        pendingDecisions.add(new PendingDecision(
                PendingDecisionType.CONFIRMATION,
                "risk.confirm",
                "确认风险与授权口径",
                "确认当前阶段是否允许进入执行，以及高风险动作是否需要继续收紧。",
                true
        ));

        boolean materialsReady = pendingDecisions.stream()
                .filter(PendingDecision::isRequired)
                .noneMatch(decision -> decision.getType() != PendingDecisionType.CONFIRMATION);

        boolean requiredConfirmationsCompleted = pendingDecisions.stream()
                .filter(PendingDecision::isRequired)
                .filter(decision -> decision.getType() == PendingDecisionType.CONFIRMATION)
                .allMatch(decision -> context.hasConfirmedDecision(decision.getCode()));

        GateStatus gateStatus;
        String blockedReason;
        if (!materialsReady) {
            gateStatus = GateStatus.BLOCKED;
            blockedReason = "stage gate is blocked: developer input is incomplete";
        } else if (!requiredConfirmationsCompleted) {
            gateStatus = GateStatus.NEEDS_CONFIRMATION;
            blockedReason = "stage gate is waiting for required confirmations";
        } else {
            gateStatus = GateStatus.PASS;
            blockedReason = null;
        }

        List<PendingDecision> unresolved = pendingDecisions.stream()
                .filter(PendingDecision::isRequired)
                .filter(decision -> decision.getType() != PendingDecisionType.CONFIRMATION
                        || !context.hasConfirmedDecision(decision.getCode()))
                .toList();

        return new RuleGateReviewResult(
                gateStatus,
                stageSummary,
                materialsReady,
                requiredConfirmationsCompleted,
                unresolved,
                feedbackItems,
                blockedReason
        );
    }

    private boolean isGoalAnalysisEmpty(TaskExecutionContext context) {
        GoalAnalysis ga = context.getGoalAnalysis();
        return ga == null || ga.equals(GoalAnalysis.empty());
    }

    private boolean isApproachPlanEmpty(TaskExecutionContext context) {
        ApproachPlan ap = context.getApproachPlan();
        return ap == null || ap.equals(ApproachPlan.empty());
    }

    private String buildStageSummary(TaskExecutionContext context,
                                     boolean goalAnalysisEmpty,
                                     boolean approachPlanEmpty) {
        StringBuilder sb = new StringBuilder();

        if (goalAnalysisEmpty) {
            sb.append("【GOAL_DEFINITION】⚠ LLM 目标分析未产出有效结果。\n");
        } else {
            GoalAnalysis ga = context.getGoalAnalysis();
            sb.append("【GOAL_DEFINITION】\n");
            sb.append("  任务类型: ").append(ga.taskType()).append("\n");
            sb.append("  目标: ").append(ga.goals()).append("\n");
            sb.append("  约束: ").append(ga.constraints()).append("\n");
            sb.append("  成功标准: ").append(ga.successCriteria()).append("\n");
        }

        if (approachPlanEmpty) {
            sb.append("【PLANNING】⚠ LLM 方法规划未产出有效结果。\n");
        } else {
            ApproachPlan ap = context.getApproachPlan();
            sb.append("【PLANNING】\n");
            sb.append("  方法: ").append(ap.approach()).append("\n");
            sb.append("  推荐工具: ").append(ap.recommendedTools()).append("\n");
            sb.append("  风险: ").append(ap.risks()).append("\n");
            sb.append("  复杂度: ").append(ap.estimatedComplexity()).append("\n");
        }

        sb.append("\n下一步需要开发者确认阶段理解、工具边界和风险授权后，系统才允许进入执行阶段。");
        return sb.toString();
    }

    private void collectUnderstandingFeedback(TaskExecutionContext context,
                                              String stageSummary,
                                              List<String> feedbackItems) {
        String normalized = stageSummary.toLowerCase(Locale.ROOT);
        if (!(normalized.contains("目标") || normalized.contains("goal"))) {
            feedbackItems.add("复述中没有明确提到任务目标。");
        }
        if (!isApproachPlanEmpty(context)
                && !(normalized.contains("工具") || normalized.contains("tool"))) {
            feedbackItems.add("复述中没有覆盖规划阶段筛出的工具范围。");
        }
        if (!(normalized.contains("阶段") || normalized.contains("门禁") || normalized.contains("确认"))) {
            feedbackItems.add("复述中没有说明当前为什么停在门禁阶段。");
        }
    }

    private void collectRiskFeedback(String riskSummary, List<String> feedbackItems) {
        String normalized = riskSummary.toLowerCase(Locale.ROOT);
        if (!(normalized.contains("风险") || normalized.contains("risk"))) {
            feedbackItems.add("风险判断中没有明确指出风险点。");
        }
        if (!(normalized.contains("授权") || normalized.contains("收紧") || normalized.contains("接管"))) {
            feedbackItems.add("风险判断中没有说明授权、收紧或人工接管口径。");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
