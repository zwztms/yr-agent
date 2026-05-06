package com.yragent.cli.formatter;

import com.yragent.domain.gate.GateReviewNote;
import com.yragent.domain.gate.PendingDecision;
import com.yragent.domain.stage.TaskExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class CliOutputFormatter {

    public String formatTaskSummary(TaskExecutionContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("taskId=").append(context.getTaskId()).append(System.lineSeparator());
        sb.append("currentStage=").append(context.getCurrentStage()).append(System.lineSeparator());
        if (context.getFailureReason() != null) {
            sb.append("failureReason=").append(context.getFailureReason()).append(System.lineSeparator());
        }
        if (context.getNextAction() != null) {
            sb.append("nextAction=").append(context.getNextAction()).append(System.lineSeparator());
        }
        if (context.getCurrentStageSummary() != null) {
            sb.append("stageSummary=").append(context.getCurrentStageSummary()).append(System.lineSeparator());
        }
        if (context.getUserPreference() != null) {
            sb.append("preferences: riskTolerance=").append(context.getUserPreference().getRiskTolerance())
                    .append(", confirmationMode=").append(context.getUserPreference().getConfirmationMode())
                    .append(", displayLanguage=").append(context.getUserPreference().getDisplayLanguage())
                    .append(System.lineSeparator());
        }
        if (context.getProjectPolicy() != null) {
            sb.append("policies: projectType=").append(context.getProjectPolicy().getProjectType())
                    .append(", directoryExclusions=").append(context.getProjectPolicy().getDirectoryExclusions())
                    .append(", allowNetworkAccess=").append(context.getProjectPolicy().isAllowNetworkAccess())
                    .append(System.lineSeparator());
        }
        sb.append("notes=").append(System.lineSeparator());
        for (String note : context.getStageNotes()) {
            sb.append(" - ").append(note).append(System.lineSeparator());
        }
        if (context.getDeveloperUnderstanding() != null) {
            sb.append("developerUnderstanding=").append(System.lineSeparator());
            sb.append(" - stageSummary: ")
                    .append(context.getDeveloperUnderstanding().getStageSummary())
                    .append(System.lineSeparator());
            sb.append(" - riskSummary: ")
                    .append(context.getDeveloperUnderstanding().getRiskSummary())
                    .append(System.lineSeparator());
        }
        GateReviewNote gateReviewNote = context.getGateReviewNote();
        if (gateReviewNote != null) {
            sb.append("gateReview=").append(gateReviewNote.getSummary()).append(System.lineSeparator());
            for (String evidenceItem : gateReviewNote.getEvidence()) {
                sb.append(" - [依据] ").append(evidenceItem).append(System.lineSeparator());
            }
            for (String feedbackItem : gateReviewNote.getFeedbackItems()) {
                sb.append(" - ").append(feedbackItem).append(System.lineSeparator());
            }
        }
        if (!context.getGateReviewAttempts().isEmpty()) {
            sb.append("gateAttempts=").append(context.getGateReviewAttempts().size())
                    .append(" 轮").append(System.lineSeparator());
            for (com.yragent.domain.gate.GateReviewAttempt attempt : context.getGateReviewAttempts()) {
                sb.append(" - 第").append(attempt.getAttemptIndex()).append("轮 | ")
                        .append(attempt.getFinalStatus()).append(" | ")
                        .append(attempt.getTimestamp()).append(System.lineSeparator());
            }
        }
        // 执行结果展示。
        if (context.getExecutionResult() != null) {
            var execResult = context.getExecutionResult();
            sb.append("execution=").append(System.lineSeparator());
            sb.append(" - completedSteps=").append(execResult.getCompletedSteps())
                    .append(", failedSteps=").append(execResult.getFailedSteps())
                    .append(System.lineSeparator());
            sb.append(execResult.getOutputSummary());
        }
        // 验证结果展示。
        if (context.getVerificationResult() != null) {
            var verifResult = context.getVerificationResult();
            sb.append("verification: passed=").append(verifResult.isPassed())
                    .append(", summary=").append(verifResult.getSummary())
                    .append(System.lineSeparator());
            if (!verifResult.getIssues().isEmpty()) {
                for (String issue : verifResult.getIssues()) {
                    sb.append(" - [问题] ").append(issue).append(System.lineSeparator());
                }
            }
        }
        // 门禁阻断时，把待确认项直接输出给开发者，避免系统停住却不知道原因。
        if (!context.getPendingDecisions().isEmpty()) {
            sb.append("pendingDecisions=").append(System.lineSeparator());
            for (PendingDecision decision : context.getPendingDecisions()) {
                sb.append(" - ").append(decision.getCode())
                        .append(" | ")
                        .append(decision.getType())
                        .append(" | ")
                        .append(decision.getTitle())
                        .append(" | ")
                        .append(decision.getDescription())
                        .append(System.lineSeparator());
            }
        }
        return sb.toString();
    }
}
