package com.yragent.domain.gate.checklist;

public record ItemCoverage(
        String itemId,
        String status,
        String evidence,
        String suggestion
) {
    public static final String COVERED = "covered";
    public static final String PARTIAL = "partial";
    public static final String MISSING = "missing";

    public boolean isCovered() {
        return COVERED.equals(status);
    }
}
