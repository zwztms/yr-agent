package com.yragent.domain.gate;

import java.util.List;

public class RuleGateReviewResult {

    private final GateStatus gateStatus;
    private final String stageSummary;
    private final boolean materialsReady;
    private final boolean requiredConfirmationsCompleted;
    private final List<PendingDecision> pendingDecisions;
    private final List<String> feedbackItems;
    private final String blockedReason;

    public RuleGateReviewResult(GateStatus gateStatus,
                                String stageSummary,
                                boolean materialsReady,
                                boolean requiredConfirmationsCompleted,
                                List<PendingDecision> pendingDecisions,
                                List<String> feedbackItems,
                                String blockedReason) {
        this.gateStatus = gateStatus;
        this.stageSummary = stageSummary;
        this.materialsReady = materialsReady;
        this.requiredConfirmationsCompleted = requiredConfirmationsCompleted;
        this.pendingDecisions = pendingDecisions;
        this.feedbackItems = feedbackItems;
        this.blockedReason = blockedReason;
    }

    public GateStatus getGateStatus() {
        return gateStatus;
    }

    public String getStageSummary() {
        return stageSummary;
    }

    public boolean isMaterialsReady() {
        return materialsReady;
    }

    public boolean isRequiredConfirmationsCompleted() {
        return requiredConfirmationsCompleted;
    }

    public List<PendingDecision> getPendingDecisions() {
        return pendingDecisions;
    }

    public List<String> getFeedbackItems() {
        return feedbackItems;
    }

    public String getBlockedReason() {
        return blockedReason;
    }
}
