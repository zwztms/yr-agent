package com.yragent.domain.gate;

public class DeveloperUnderstanding {

    private final String stageSummary;
    private final String riskSummary;

    public DeveloperUnderstanding(String stageSummary, String riskSummary) {
        this.stageSummary = stageSummary;
        this.riskSummary = riskSummary;
    }

    public String getStageSummary() {
        return stageSummary;
    }

    public String getRiskSummary() {
        return riskSummary;
    }
}
