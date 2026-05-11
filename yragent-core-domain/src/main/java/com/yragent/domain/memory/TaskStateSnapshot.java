package com.yragent.domain.memory;

import com.yragent.domain.stage.TaskExecutionContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// 任务状态快照：用于阶段完成后持久化任务关键状态，供中断恢复使用。
public class TaskStateSnapshot {

    private String taskId;
    private String currentStage;
    private List<String> stageNotes;
    private List<String> confirmedDecisionCodes;
    private String understandingSummary;
    private String riskSummary;
    private String failureReason;
    private String timestamp;

    public TaskStateSnapshot() {
    }

    // 从任务上下文提取关键状态，不存完整对象，只存必要字段。
    public static TaskStateSnapshot from(TaskExecutionContext context) {
        TaskStateSnapshot snapshot = new TaskStateSnapshot();
        snapshot.taskId = context.getTaskId();
        snapshot.currentStage = context.getCurrentStage() != null ? context.getCurrentStage().name() : null;
        snapshot.stageNotes = new ArrayList<>(context.getStageNotes());
        snapshot.confirmedDecisionCodes = new ArrayList<>(context.getConfirmedDecisionCodes());
        if (context.getDeveloperUnderstanding() != null) {
            snapshot.understandingSummary = context.getDeveloperUnderstanding().getStageSummary();
            snapshot.riskSummary = context.getDeveloperUnderstanding().getRiskSummary();
        }
        snapshot.failureReason = context.getFailureReason();
        snapshot.timestamp = Instant.now().toString();
        return snapshot;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
    }

    public List<String> getStageNotes() {
        return stageNotes;
    }

    public void setStageNotes(List<String> stageNotes) {
        this.stageNotes = stageNotes;
    }

    public List<String> getConfirmedDecisionCodes() {
        return confirmedDecisionCodes;
    }

    public void setConfirmedDecisionCodes(List<String> confirmedDecisionCodes) {
        this.confirmedDecisionCodes = confirmedDecisionCodes;
    }

    public String getUnderstandingSummary() {
        return understandingSummary;
    }

    public void setUnderstandingSummary(String understandingSummary) {
        this.understandingSummary = understandingSummary;
    }

    public String getRiskSummary() {
        return riskSummary;
    }

    public void setRiskSummary(String riskSummary) {
        this.riskSummary = riskSummary;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
