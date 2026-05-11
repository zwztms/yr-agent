package com.yragent.domain.stage;

import com.yragent.domain.gate.GateReviewNote;
import com.yragent.domain.gate.PendingDecision;

import java.util.List;

public class StageResult {

    private final StageType stageType;
    private final boolean passed;
    private final String summary;
    private final String stageSummary;
    private final GateReviewNote gateReviewNote;
    // 门禁阶段会把待确认项挂在这里，供编排器和 CLI 读取。
    private final List<PendingDecision> pendingDecisions;
    // nextAction/failureReason 先作为最小可见结果，后续再演进成更完整的失败结构。
    private final String nextAction;
    private final String failureReason;

    public StageResult(StageType stageType, boolean passed, String summary) {
        this(stageType, passed, summary, null, null, List.of(), null, null);
    }

    public StageResult(StageType stageType,
                       boolean passed,
                       String summary,
                       String stageSummary,
                       GateReviewNote gateReviewNote,
                       List<PendingDecision> pendingDecisions,
                       String nextAction,
                       String failureReason) {
        this.stageType = stageType;
        this.passed = passed;
        this.summary = summary;
        this.stageSummary = stageSummary;
        this.gateReviewNote = gateReviewNote;
        this.pendingDecisions = pendingDecisions;
        this.nextAction = nextAction;
        this.failureReason = failureReason;
    }

    public StageType getStageType() {
        return stageType;
    }

    public boolean isPassed() {
        return passed;
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

    public String getNextAction() {
        return nextAction;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
