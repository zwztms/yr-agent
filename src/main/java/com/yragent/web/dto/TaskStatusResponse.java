package com.yragent.web.dto;

import com.yragent.domain.gate.PendingDecision;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;

import java.util.List;

public record TaskStatusResponse(
        String taskId,
        String currentStage,
        List<String> stageNotes,
        List<PendingDecisionInfo> pendingDecisions,
        String stageSummary,
        String gateReviewNote,
        List<String> gateReviewFeedback,
        String nextAction,
        String failureReason,
        boolean completed
) {
    public record PendingDecisionInfo(
            String code,
            String type,
            String title,
            String description,
            boolean required
    ) {}

    public static TaskStatusResponse from(TaskExecutionContext context) {
        List<PendingDecisionInfo> decisions = context.getPendingDecisions().stream()
                .map(d -> new PendingDecisionInfo(
                        d.getCode(), d.getType().name(), d.getTitle(), d.getDescription(), d.isRequired()))
                .toList();
        List<String> gateFeedback = context.getGateReviewNote() != null
                ? context.getGateReviewNote().getFeedbackItems()
                : List.of();
        return new TaskStatusResponse(
                context.getTaskId(),
                context.getCurrentStage() != null ? context.getCurrentStage().name() : null,
                context.getStageNotes(),
                decisions,
                context.getCurrentStageSummary(),
                context.getGateReviewNote() != null ? context.getGateReviewNote().getSummary() : null,
                gateFeedback,
                context.getNextAction(),
                context.getFailureReason(),
                decisions.isEmpty() && context.getCurrentStage() == StageType.REVIEW
        );
    }
}
