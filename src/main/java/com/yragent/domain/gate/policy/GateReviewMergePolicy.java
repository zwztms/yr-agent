package com.yragent.domain.gate.policy;

import com.yragent.domain.gate.GateSemanticReviewResult;
import com.yragent.domain.gate.GateStatus;
import com.yragent.domain.gate.MergedGateReviewResult;
import com.yragent.domain.gate.PendingDecision;
import com.yragent.domain.gate.PendingDecisionType;
import com.yragent.domain.gate.RuleGateReviewResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GateReviewMergePolicy {

    public MergedGateReviewResult merge(RuleGateReviewResult ruleResult,
                                        GateSemanticReviewResult semanticResult) {
        return new MergedGateReviewResult(
                resolveGateStatus(ruleResult, semanticResult),
                ruleResult.getStageSummary(),
                mergePendingDecisions(ruleResult, semanticResult),
                mergeFeedbackItems(ruleResult, semanticResult),
                semanticResult.isReviewApplied(),
                semanticResult.isFallbackToRuleOnly()
        );
    }

    private GateStatus resolveGateStatus(RuleGateReviewResult ruleResult,
                                         GateSemanticReviewResult semanticResult) {
        if (!ruleResult.isMaterialsReady()) {
            return GateStatus.BLOCKED;
        }
        if (semanticResult.isReviewApplied() && semanticResult.requiresClarification()) {
            return GateStatus.NEEDS_CLARIFICATION;
        }
        if (!ruleResult.isRequiredConfirmationsCompleted()) {
            return GateStatus.NEEDS_CONFIRMATION;
        }
        return GateStatus.PASS;
    }

    private List<PendingDecision> mergePendingDecisions(RuleGateReviewResult ruleResult,
                                                        GateSemanticReviewResult semanticResult) {
        List<PendingDecision> merged = new ArrayList<>(ruleResult.getPendingDecisions());
        if (semanticResult.isReviewApplied() && semanticResult.requiresClarification()) {
            if (!semanticResult.getMissingTopics().isEmpty() || !semanticResult.getMisunderstandings().isEmpty()) {
                addIfAbsent(merged, new PendingDecision(
                        PendingDecisionType.UNDERSTANDING_INPUT,
                        "understanding.summary",
                        "补充当前阶段理解",
                        buildUnderstandingDescription(semanticResult),
                        true
                ));
            }
            if (!semanticResult.getRiskBlindSpots().isEmpty()) {
                addIfAbsent(merged, new PendingDecision(
                        PendingDecisionType.RISK_INPUT,
                        "risk.summary",
                        "补充风险判断",
                        buildRiskDescription(semanticResult),
                        true
                ));
            }
        }
        return merged;
    }

    private String buildUnderstandingDescription(GateSemanticReviewResult semanticResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下具体发现补充当前阶段理解：");

        List<String> questions = semanticResult.getSuggestedQuestions();
        if (!questions.isEmpty()) {
            questions.forEach(q -> sb.append("\n  · ").append(q));
        }

        if (!semanticResult.getMissingTopics().isEmpty()) {
            sb.append("\n\n缺失的主题：");
            semanticResult.getMissingTopics().forEach(t -> sb.append("\n  · ").append(t));
        }

        if (!semanticResult.getMisunderstandings().isEmpty()) {
            sb.append("\n\n疑似误解：");
            semanticResult.getMisunderstandings().forEach(m -> sb.append("\n  · ").append(m));
        }

        return sb.toString();
    }

    private String buildRiskDescription(GateSemanticReviewResult semanticResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下具体发现补充风险判断：");

        if (!semanticResult.getRiskBlindSpots().isEmpty()) {
            semanticResult.getRiskBlindSpots().forEach(r -> sb.append("\n  · ").append(r));
        }

        List<String> questions = semanticResult.getSuggestedQuestions();
        if (!questions.isEmpty()) {
            sb.append("\n\n建议关注：");
            questions.forEach(q -> sb.append("\n  · ").append(q));
        }

        return sb.toString();
    }

    private List<String> mergeFeedbackItems(RuleGateReviewResult ruleResult,
                                            GateSemanticReviewResult semanticResult) {
        List<String> merged = new ArrayList<>(ruleResult.getFeedbackItems());
        merged.addAll(semanticResult.getFeedbackItems());
        return merged;
    }

    private void addIfAbsent(List<PendingDecision> pendingDecisions, PendingDecision candidate) {
        boolean exists = pendingDecisions.stream()
                .anyMatch(item -> item.getCode().equals(candidate.getCode())
                        && item.getType() == candidate.getType());
        if (!exists) {
            pendingDecisions.add(candidate);
        }
    }
}
