package com.yragent.domain.gate;

import java.time.Instant;
import java.util.List;

// 单轮门禁尝试记录，保存开发者输入、规则结果、语义结果、合并裁决和最终状态的完整快照。
// 不可变对象，全参构造。
public class GateReviewAttempt {

    private final int attemptIndex;
    private final Instant timestamp;
    private final DeveloperUnderstanding developerUnderstanding;
    private final RuleGateReviewResult ruleResult;
    private final GateSemanticReviewResult semanticResult;
    private final MergedGateReviewResult mergedResult;
    private final GateStatus finalStatus;
    private final List<String> attemptNotes;

    public GateReviewAttempt(int attemptIndex,
                             Instant timestamp,
                             DeveloperUnderstanding developerUnderstanding,
                             RuleGateReviewResult ruleResult,
                             GateSemanticReviewResult semanticResult,
                             MergedGateReviewResult mergedResult,
                             GateStatus finalStatus,
                             List<String> attemptNotes) {
        this.attemptIndex = attemptIndex;
        this.timestamp = timestamp;
        this.developerUnderstanding = developerUnderstanding;
        this.ruleResult = ruleResult;
        this.semanticResult = semanticResult;
        this.mergedResult = mergedResult;
        this.finalStatus = finalStatus;
        this.attemptNotes = attemptNotes;
    }

    public int getAttemptIndex() {
        return attemptIndex;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public DeveloperUnderstanding getDeveloperUnderstanding() {
        return developerUnderstanding;
    }

    public RuleGateReviewResult getRuleResult() {
        return ruleResult;
    }

    public GateSemanticReviewResult getSemanticResult() {
        return semanticResult;
    }

    public MergedGateReviewResult getMergedResult() {
        return mergedResult;
    }

    public GateStatus getFinalStatus() {
        return finalStatus;
    }

    public List<String> getAttemptNotes() {
        return attemptNotes;
    }
}
