package com.yragent.domain.gate;

import java.util.List;

public class GateSemanticReviewResult {

    private final boolean reviewApplied;
    private final boolean fallbackToRuleOnly;
    private final boolean coveragePassed;
    private final List<String> missingTopics;
    private final List<String> misunderstandings;
    private final List<String> riskBlindSpots;
    private final List<String> suggestedQuestions;
    private final List<String> feedbackItems;
    private final String fallbackReason;

    public GateSemanticReviewResult(boolean reviewApplied,
                                    boolean fallbackToRuleOnly,
                                    boolean coveragePassed,
                                    List<String> missingTopics,
                                    List<String> misunderstandings,
                                    List<String> riskBlindSpots,
                                    List<String> suggestedQuestions,
                                    List<String> feedbackItems,
                                    String fallbackReason) {
        this.reviewApplied = reviewApplied;
        this.fallbackToRuleOnly = fallbackToRuleOnly;
        this.coveragePassed = coveragePassed;
        this.missingTopics = missingTopics;
        this.misunderstandings = misunderstandings;
        this.riskBlindSpots = riskBlindSpots;
        this.suggestedQuestions = suggestedQuestions;
        this.feedbackItems = feedbackItems;
        this.fallbackReason = fallbackReason;
    }

    public static GateSemanticReviewResult notApplied() {
        return new GateSemanticReviewResult(
                false,
                false,
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    public static GateSemanticReviewResult fallback(String fallbackReason) {
        return new GateSemanticReviewResult(
                false,
                true,
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("语义评审暂不可用，本轮按规则门禁继续执行。"),
                fallbackReason
        );
    }

    public boolean isReviewApplied() {
        return reviewApplied;
    }

    public boolean isFallbackToRuleOnly() {
        return fallbackToRuleOnly;
    }

    public boolean isCoveragePassed() {
        return coveragePassed;
    }

    public List<String> getMissingTopics() {
        return missingTopics;
    }

    public List<String> getMisunderstandings() {
        return misunderstandings;
    }

    public List<String> getRiskBlindSpots() {
        return riskBlindSpots;
    }

    public List<String> getSuggestedQuestions() {
        return suggestedQuestions;
    }

    public List<String> getFeedbackItems() {
        return feedbackItems;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public boolean requiresClarification() {
        return !coveragePassed
                || !missingTopics.isEmpty()
                || !misunderstandings.isEmpty()
                || !riskBlindSpots.isEmpty();
    }
}
