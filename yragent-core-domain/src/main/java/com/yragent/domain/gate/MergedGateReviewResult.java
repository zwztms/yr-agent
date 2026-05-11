package com.yragent.domain.gate;

import java.util.List;

public class MergedGateReviewResult {

    private final GateStatus gateStatus;
    private final String stageSummary;
    private final List<PendingDecision> pendingDecisions;
    private final List<String> feedbackItems;
    private final boolean llmReviewApplied;
    private final boolean fallbackToRuleOnly;

    public MergedGateReviewResult(GateStatus gateStatus,
                                  String stageSummary,
                                  List<PendingDecision> pendingDecisions,
                                  List<String> feedbackItems,
                                  boolean llmReviewApplied,
                                  boolean fallbackToRuleOnly) {
        this.gateStatus = gateStatus;
        this.stageSummary = stageSummary;
        this.pendingDecisions = pendingDecisions;
        this.feedbackItems = feedbackItems;
        this.llmReviewApplied = llmReviewApplied;
        this.fallbackToRuleOnly = fallbackToRuleOnly;
    }

    public GateStatus getGateStatus() {
        return gateStatus;
    }

    public String getStageSummary() {
        return stageSummary;
    }

    public List<PendingDecision> getPendingDecisions() {
        return pendingDecisions;
    }

    public List<String> getFeedbackItems() {
        return feedbackItems;
    }

    public boolean isLlmReviewApplied() {
        return llmReviewApplied;
    }

    public boolean isFallbackToRuleOnly() {
        return fallbackToRuleOnly;
    }
}
