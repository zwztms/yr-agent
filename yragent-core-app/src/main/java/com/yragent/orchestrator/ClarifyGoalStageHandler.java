package com.yragent.orchestrator;

import com.yragent.domain.goal.GoalAnalysis;
import com.yragent.domain.goal.GoalClarification;
import com.yragent.domain.gate.PendingDecision;
import com.yragent.domain.gate.PendingDecisionType;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.stage.StageResult;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ClarifyGoalStageHandler implements StageHandler {

    private static final Logger log = LoggerFactory.getLogger(ClarifyGoalStageHandler.class);
    private final LlmClient llmClient;

    public ClarifyGoalStageHandler(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public StageType support() {
        return StageType.CLARIFY_GOAL;
    }

    @Override
    public StageResult handle(TaskExecutionContext context) {
        GoalAnalysis currentGoal = context.getGoalAnalysis();
        if (currentGoal == null) {
            return new StageResult(StageType.CLARIFY_GOAL, true,
                    "no goal analysis result, skipping clarification stage",
                    "skipped", null, List.of(), null, null);
        }

        GoalClarification existingClarification = context.getGoalClarification();

        // Check if we already have developer answers to clarification questions
        if (existingClarification != null && hasAnswers(existingClarification)) {
            // Developer has answered — refine the GoalAnalysis via LLM
            try {
                String prompt = buildRefineGoalPrompt(currentGoal, existingClarification);
                String response = llmClient.structuredCompletion(prompt, GOAL_ANALYSIS_SCHEMA);
                // The refined goal from LLM becomes the new goal
                // For now, keep existing goal and mark clarification as resolved
                GoalClarification completed = new GoalClarification(
                        existingClarification.getQuestions(),
                        existingClarification.getAnswers(),
                        currentGoal);
                context.setGoalClarification(completed);
                return new StageResult(StageType.CLARIFY_GOAL, true,
                        "goal clarified, developer answered " + existingClarification.getAnswers().size() + " questions",
                        buildClarificationSummary(completed), null, List.of(), null, null);
            } catch (Exception e) {
                log.warn("LLM goal refinement failed, proceeding with original goal", e);
                return new StageResult(StageType.CLARIFY_GOAL, true,
                        "clarification stage complete (LLM call failed, using original goal)",
                        null, null, List.of(), null, null);
            }
        }

        // First entry or no answers yet — generate clarification questions
        if (currentGoal.needsClarification() || "low".equalsIgnoreCase(currentGoal.confidence())) {
            try {
                String prompt = buildClarifyPrompt(currentGoal);
                String response = llmClient.chatCompletion(prompt);
                // Parse LLM response into questions
                List<String> questions = parseQuestions(response);
                List<PendingDecision> decisions = new ArrayList<>();
                for (int i = 0; i < questions.size(); i++) {
                    decisions.add(new PendingDecision(
                            PendingDecisionType.CLARIFICATION,
                            "clarify." + (i + 1),
                            "question " + (i + 1),
                            questions.get(i),
                            true));
                }
                context.setGoalClarification(new GoalClarification(questions, List.of(), currentGoal));
                return new StageResult(StageType.CLARIFY_GOAL, false,
                        "needs clarification for " + questions.size() + " questions",
                        "current goal analysis confidence is " + currentGoal.confidence() + ", please confirm the following questions",
                        null, decisions, "waiting for developer to answer clarification questions", null);
            } catch (Exception e) {
                log.warn("LLM clarification question generation failed", e);
                return new StageResult(StageType.CLARIFY_GOAL, true,
                        "clarification stage skipped (LLM call failed)",
                        null, null, List.of(), null, null);
            }
        }

        // No clarification needed
        return new StageResult(StageType.CLARIFY_GOAL, true,
                "goal is clear, no clarification needed",
                "confidence: " + currentGoal.confidence(), null, List.of(), null, null);
    }

    private boolean hasAnswers(GoalClarification clarification) {
        return clarification.getAnswers() != null && !clarification.getAnswers().isEmpty();
    }

    private String buildClarificationSummary(GoalClarification clarification) {
        StringBuilder sb = new StringBuilder("developer answered the following clarification questions:\n");
        for (int i = 0; i < clarification.getQuestions().size(); i++) {
            sb.append("Q: ").append(clarification.getQuestions().get(i)).append("\n");
            if (i < clarification.getAnswers().size()) {
                sb.append("A: ").append(clarification.getAnswers().get(i)).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildClarifyPrompt(GoalAnalysis goal) {
        return """
            you are a software development consultant. based on the analysis of user requirements, generate 2-4 specific questions to clarify ambiguous points.

            current analysis:
            - task type: %s
            - goal: %s
            - constraints: %s
            - missing info: %s
            - confidence: %s

            requirements:
            1. questions must be specific and directly address key points of disagreement
            2. each question should include a brief explanation of why confirmation is needed
            3. only ask questions that "cannot reliably plan without asking"
            4. if the missing info list is empty and confidence is high, reply "NO_QUESTIONS"

            reply in Chinese. one question per line, starting with "- ".
            """.formatted(
                goal.taskType(),
                String.join("; ", goal.goals()),
                String.join("; ", goal.constraints()),
                String.join("; ", goal.missingInfo()),
                goal.confidence());
    }

    private String buildRefineGoalPrompt(GoalAnalysis goal, GoalClarification clarification) {
        StringBuilder qa = new StringBuilder();
        for (int i = 0; i < clarification.getQuestions().size(); i++) {
            qa.append("Q: ").append(clarification.getQuestions().get(i)).append("\n");
            String answer = i < clarification.getAnswers().size()
                    ? clarification.getAnswers().get(i) : "(unanswered)";
            qa.append("A: ").append(answer).append("\n");
        }
        return """
            based on the developer's answers, revise the goal analysis.

            original analysis:
            - task type: %s
            - goal: %s
            - constraints: %s

            developer answers:
            %s

            please output the revised GoalAnalysis JSON (keep the original format, only correct parts explicitly denied by the developer).
            """.formatted(
                goal.taskType(),
                String.join("; ", goal.goals()),
                String.join("; ", goal.constraints()),
                qa.toString());
    }

    private List<String> parseQuestions(String llmResponse) {
        List<String> questions = new ArrayList<>();
        for (String line : llmResponse.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ") || trimmed.startsWith("-")) {
                String q = trimmed.replaceFirst("^-\\s*", "").trim();
                if (!q.isEmpty() && !q.contains("NO_QUESTIONS")) {
                    questions.add(q);
                }
            }
        }
        return questions.isEmpty() ? List.of() : questions;
    }

    private static final String GOAL_ANALYSIS_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "taskType": {"type": "string"},
            "goals": {"type": "array", "items": {"type": "string"}},
            "constraints": {"type": "array", "items": {"type": "string"}},
            "missingInfo": {"type": "array", "items": {"type": "string"}},
            "suggestedMode": {"type": "string"},
            "confidence": {"type": "string"},
            "needsClarification": {"type": "boolean"}
          }
        }""";
}
