package com.yragent.domain.gate;

import java.util.List;

public class LlmGateReviewResponse {

    private boolean coveragePassed;
    private List<String> coveredTopics;
    private List<String> missingTopics;
    private List<String> misunderstandings;
    private List<String> riskBlindSpots;
    private List<String> suggestedQuestions;
    private String rawSummary;

    public boolean isCoveragePassed() {
        return coveragePassed;
    }

    public void setCoveragePassed(boolean coveragePassed) {
        this.coveragePassed = coveragePassed;
    }

    public List<String> getCoveredTopics() {
        return coveredTopics;
    }

    public void setCoveredTopics(List<String> coveredTopics) {
        this.coveredTopics = coveredTopics;
    }

    public List<String> getMissingTopics() {
        return missingTopics;
    }

    public void setMissingTopics(List<String> missingTopics) {
        this.missingTopics = missingTopics;
    }

    public List<String> getMisunderstandings() {
        return misunderstandings;
    }

    public void setMisunderstandings(List<String> misunderstandings) {
        this.misunderstandings = misunderstandings;
    }

    public List<String> getRiskBlindSpots() {
        return riskBlindSpots;
    }

    public void setRiskBlindSpots(List<String> riskBlindSpots) {
        this.riskBlindSpots = riskBlindSpots;
    }

    public List<String> getSuggestedQuestions() {
        return suggestedQuestions;
    }

    public void setSuggestedQuestions(List<String> suggestedQuestions) {
        this.suggestedQuestions = suggestedQuestions;
    }

    public String getRawSummary() {
        return rawSummary;
    }

    public void setRawSummary(String rawSummary) {
        this.rawSummary = rawSummary;
    }
}
