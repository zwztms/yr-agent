package com.yragent.domain.execution;

import java.util.List;
import java.util.Map;

// LLM 生成的执行计划。不可变对象。
public class ExecutionPlan {

    private final List<ExecutionStep> steps;
    private final String rationale;

    public ExecutionPlan(List<ExecutionStep> steps, String rationale) {
        this.steps = steps != null ? List.copyOf(steps) : List.of();
        this.rationale = rationale != null ? rationale : "";
    }

    public List<ExecutionStep> getSteps() {
        return steps;
    }

    public String getRationale() {
        return rationale;
    }

    // 单个执行步骤。
    public record ExecutionStep(int index, String tool, Map<String, String> params, String reason) {}
}
