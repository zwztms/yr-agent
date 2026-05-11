package com.yragent.domain.execution;

import com.yragent.domain.tool.ToolExecutor.ToolExecutionResult;

import java.util.List;

// 执行结果汇总。不可变对象。
public class ExecutionResult {

    private final ExecutionPlan plan;
    private final List<ToolExecutionResult> stepResults;
    private final int completedSteps;
    private final int failedSteps;
    private final String outputSummary;

    public ExecutionResult(ExecutionPlan plan, List<ToolExecutionResult> stepResults,
                           int completedSteps, int failedSteps, String outputSummary) {
        this.plan = plan;
        this.stepResults = stepResults != null ? List.copyOf(stepResults) : List.of();
        this.completedSteps = completedSteps;
        this.failedSteps = failedSteps;
        this.outputSummary = outputSummary != null ? outputSummary : "";
    }

    public ExecutionPlan getPlan() {
        return plan;
    }

    public List<ToolExecutionResult> getStepResults() {
        return stepResults;
    }

    public int getCompletedSteps() {
        return completedSteps;
    }

    public int getFailedSteps() {
        return failedSteps;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public boolean allStepsSucceeded() {
        return failedSteps == 0;
    }
}
