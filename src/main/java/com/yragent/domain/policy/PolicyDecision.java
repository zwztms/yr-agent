package com.yragent.domain.policy;

public class PolicyDecision {

    private final PolicyDecisionType type;
    private final String reason;

    public PolicyDecision(PolicyDecisionType type, String reason) {
        this.type = type;
        this.reason = reason;
    }

    public PolicyDecisionType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }
}
