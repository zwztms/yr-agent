package com.yragent.domain.gate;

import java.util.List;

public class GateReviewNote {

    private final GateStatus gateStatus;
    private final String summary;
    private final List<String> feedbackItems;
    // 裁决依据：按条记录规则层、语义层和合并裁决的判断理由。
    private final List<String> evidence;

    public GateReviewNote(GateStatus gateStatus, String summary, List<String> feedbackItems, List<String> evidence) {
        this.gateStatus = gateStatus;
        this.summary = summary;
        this.feedbackItems = feedbackItems;
        this.evidence = evidence;
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

    public List<String> getFeedbackItems() {
        return feedbackItems;
    }

    public List<String> getEvidence() {
        return evidence;
    }
}
