package com.yragent.domain.gate;

import java.util.List;

public class GateCheckResult {

    private final GateStatus gateStatus;
    private final String summary;
    private final String stageSummary;
    private final GateReviewNote gateReviewNote;
    private final List<PendingDecision> pendingDecisions;

    public GateCheckResult(GateStatus gateStatus,
                           String summary,
                           String stageSummary,
                           GateReviewNote gateReviewNote,
                           List<PendingDecision> pendingDecisions) {
        this.gateStatus = gateStatus;
        this.summary = summary;
        this.stageSummary = stageSummary;
        this.gateReviewNote = gateReviewNote;
        this.pendingDecisions = pendingDecisions;
    }

    public boolean isPassed() {
        return gateStatus.isPassed();
    }

    public GateStatus getGateStatus() {
        return gateStatus;
    }

    public String getSummary() {
        return summary;
    }

    public String getStageSummary() {
        return stageSummary;
    }

    public GateReviewNote getGateReviewNote() {
        return gateReviewNote;
    }

    public List<PendingDecision> getPendingDecisions() {
        return pendingDecisions;
    }
}
