package com.yragent.domain.gate;

import com.yragent.domain.stage.StageType;

import java.util.List;

public class LlmGateReviewRequest {

    private final String taskId;
    private final StageType stage;
    private final String stageSummary;
    private final List<String> stageNotes;
    private final String developerStageSummary;
    private final String developerRiskSummary;
    private final List<String> requiredTopics;
    private final List<String> currentPendingConfirmations;
    private final String reviewGoal;

    public LlmGateReviewRequest(String taskId,
                                StageType stage,
                                String stageSummary,
                                List<String> stageNotes,
                                String developerStageSummary,
                                String developerRiskSummary,
                                List<String> requiredTopics,
                                List<String> currentPendingConfirmations,
                                String reviewGoal) {
        this.taskId = taskId;
        this.stage = stage;
        this.stageSummary = stageSummary;
        this.stageNotes = stageNotes;
        this.developerStageSummary = developerStageSummary;
        this.developerRiskSummary = developerRiskSummary;
        this.requiredTopics = requiredTopics;
        this.currentPendingConfirmations = currentPendingConfirmations;
        this.reviewGoal = reviewGoal;
    }

    public String getTaskId() {
        return taskId;
    }

    public StageType getStage() {
        return stage;
    }

    public String getStageSummary() {
        return stageSummary;
    }

    public List<String> getStageNotes() {
        return stageNotes;
    }

    public String getDeveloperStageSummary() {
        return developerStageSummary;
    }

    public String getDeveloperRiskSummary() {
        return developerRiskSummary;
    }

    public List<String> getRequiredTopics() {
        return requiredTopics;
    }

    public List<String> getCurrentPendingConfirmations() {
        return currentPendingConfirmations;
    }

    public String getReviewGoal() {
        return reviewGoal;
    }
}
