package com.yragent.domain.goal;

import java.util.List;

// 目标澄清交互数据。由 CLARIFY_GOAL 阶段产出，包含 LLM 生成的澄清问题和开发者回答。
public class GoalClarification {

    private final List<String> questions;
    private final List<String> answers;
    private final GoalAnalysis refinedGoal;

    public GoalClarification(List<String> questions, List<String> answers, GoalAnalysis refinedGoal) {
        this.questions = questions != null ? List.copyOf(questions) : List.of();
        this.answers = answers != null ? List.copyOf(answers) : List.of();
        this.refinedGoal = refinedGoal;
    }

    public List<String> getQuestions() {
        return questions;
    }

    public List<String> getAnswers() {
        return answers;
    }

    public GoalAnalysis getRefinedGoal() {
        return refinedGoal;
    }
}
