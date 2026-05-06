package com.yragent.domain.gate;

public enum GateStatus {
    PASS,
    BLOCKED,
    NEEDS_CLARIFICATION,
    NEEDS_CONFIRMATION;

    public boolean isPassed() {
        return this == PASS;
    }
}
