package com.yragent.domain.stage;

import com.yragent.domain.execution.ExecutionResult;
import com.yragent.domain.planning.PlanDocument;
import com.yragent.domain.verification.VerificationResult;

// 多轮记录：保存单个执行轮的完整快照，包括计划、执行、验证和评审摘要。
public class RoundRecord {

    private final int roundNumber;
    private final PlanDocument plan;
    private final ExecutionResult execution;
    private final VerificationResult verification;
    private final String reviewSummary;
    private final boolean projectComplete;

    public RoundRecord(int roundNumber, PlanDocument plan, ExecutionResult execution,
                       VerificationResult verification, String reviewSummary, boolean projectComplete) {
        this.roundNumber = roundNumber;
        this.plan = plan;
        this.execution = execution;
        this.verification = verification;
        this.reviewSummary = reviewSummary;
        this.projectComplete = projectComplete;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public PlanDocument getPlan() {
        return plan;
    }

    public ExecutionResult getExecution() {
        return execution;
    }

    public VerificationResult getVerification() {
        return verification;
    }

    public String getReviewSummary() {
        return reviewSummary;
    }

    public boolean isProjectComplete() {
        return projectComplete;
    }
}
