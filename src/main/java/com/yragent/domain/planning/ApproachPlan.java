package com.yragent.domain.planning;

import java.util.List;

// LLM 高层规划结果。由 PLANNING 阶段产出，供 EXECUTION 阶段引用。
public record ApproachPlan(
        String approach,
        List<String> recommendedTools,
        List<String> risks,
        String estimatedComplexity) {

    public static ApproachPlan empty() {
        return new ApproachPlan("", List.of(), List.of(), "low");
    }
}
