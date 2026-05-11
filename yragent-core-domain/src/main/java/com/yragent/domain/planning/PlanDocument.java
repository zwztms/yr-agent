package com.yragent.domain.planning;

import java.util.List;

// 详细计划文档。由 PLANNING 阶段产出，包含架构方案、文件结构、分步计划和风险评估。
public class PlanDocument {

    private final String overview;
    private final String architecture;
    private final List<String> fileStructure;
    private final List<PlanStep> steps;
    private final List<String> risks;
    private final String estimatedComplexity;

    public PlanDocument(String overview, String architecture, List<String> fileStructure,
                        List<PlanStep> steps, List<String> risks, String estimatedComplexity) {
        this.overview = overview;
        this.architecture = architecture;
        this.fileStructure = fileStructure != null ? List.copyOf(fileStructure) : List.of();
        this.steps = steps != null ? List.copyOf(steps) : List.of();
        this.risks = risks != null ? List.copyOf(risks) : List.of();
        this.estimatedComplexity = estimatedComplexity != null ? estimatedComplexity : "medium";
    }

    public String getOverview() {
        return overview;
    }

    public String getArchitecture() {
        return architecture;
    }

    public List<String> getFileStructure() {
        return fileStructure;
    }

    public List<PlanStep> getSteps() {
        return steps;
    }

    public List<String> getRisks() {
        return risks;
    }

    public String getEstimatedComplexity() {
        return estimatedComplexity;
    }

    // 计划中的单个步骤，描述目标、工具、描述和预期产出。
    public static class PlanStep {

        private final int stepNumber;
        private final String goal;
        private final String tool;
        private final String description;
        private final List<String> expectedOutputs;

        public PlanStep(int stepNumber, String goal, String tool, String description, List<String> expectedOutputs) {
            this.stepNumber = stepNumber;
            this.goal = goal;
            this.tool = tool;
            this.description = description;
            this.expectedOutputs = expectedOutputs != null ? List.copyOf(expectedOutputs) : List.of();
        }

        public int getStepNumber() {
            return stepNumber;
        }

        public String getGoal() {
            return goal;
        }

        public String getTool() {
            return tool;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getExpectedOutputs() {
            return expectedOutputs;
        }
    }
}
