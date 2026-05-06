package com.yragent.domain.goal;

import java.util.List;

// LLM 目标分析结果。由 GOAL_DEFINITION 阶段产出，供下游阶段引用。
public record GoalAnalysis(
        String taskType,
        List<String> goals,
        List<String> constraints,
        List<String> successCriteria) {

    public static GoalAnalysis empty() {
        return new GoalAnalysis("other", List.of(), List.of(), List.of());
    }
}
