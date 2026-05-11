package com.yragent.domain.gate;

import com.yragent.domain.gate.step.GateSemanticReviewStep;
import com.yragent.domain.memory.GateReviewAttemptSerializer;
import com.yragent.domain.memory.MemoryFragment;
import com.yragent.domain.memory.MemoryService;
import com.yragent.domain.memory.MemoryType;
import com.yragent.domain.stage.TaskExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class StageGateEngine {

    private static final Logger log = LoggerFactory.getLogger(StageGateEngine.class);

    private final GateSemanticReviewStep gateSemanticReviewStep;
    private final MemoryService memoryService;
    private final GateReviewAttemptSerializer attemptSerializer;

    public StageGateEngine(GateSemanticReviewStep gateSemanticReviewStep,
                           MemoryService memoryService,
                           GateReviewAttemptSerializer attemptSerializer) {
        this.gateSemanticReviewStep = gateSemanticReviewStep;
        this.memoryService = memoryService;
        this.attemptSerializer = attemptSerializer;
    }

    public GateCheckResult evaluate(TaskExecutionContext context) {
        int attemptIndex = context.getGateReviewAttempts().size() + 1;
        Instant timestamp = Instant.now();

        // Upstream validation: both empty = immediate BLOCKED
        boolean hasGoal = context.getGoalAnalysis() != null && !context.getGoalAnalysis().goals().isEmpty();
        boolean hasPlan = context.getPlanDocument() != null && !context.getPlanDocument().getSteps().isEmpty();
        boolean hasOldPlan = context.getApproachPlan() != null;

        if (!hasGoal && !hasPlan && !hasOldPlan) {
            List<PendingDecision> decisions = List.of(
                    new PendingDecision(PendingDecisionType.UNDERSTANDING_INPUT,
                            "design.input", "supplement design info",
                            "goal analysis and plan are both empty, please complete upstream stages first", true));
            GateReviewNote note = new GateReviewNote(GateStatus.BLOCKED,
                    "missing goal and plan", List.of("please complete goal analysis and planning stages first"), List.of("upstream validation"));
            GateCheckResult result = new GateCheckResult(GateStatus.BLOCKED,
                    "missing upstream output, cannot perform gate review", null, note, decisions);
            recordAttempt(context, attemptIndex, timestamp, null, result);
            return result;
        }

        // V2: Single LLM review (no rule layer)
        GateSemanticReviewResult semanticResult = gateSemanticReviewStep.review(context);
        GateCheckResult result = buildFromSemanticResult(semanticResult, context);
        recordAttempt(context, attemptIndex, timestamp, semanticResult, result);
        return result;
    }

    private GateCheckResult buildFromSemanticResult(GateSemanticReviewResult semantic, TaskExecutionContext context) {
        GateStatus status;
        if (semantic.isFallbackToRuleOnly()) {
            status = semantic.isCoveragePassed() ? GateStatus.PASS : GateStatus.BLOCKED;
        } else if (semantic.isCoveragePassed()) {
            status = GateStatus.PASS;
        } else if (!semantic.getMissingTopics().isEmpty() || !semantic.getSuggestedQuestions().isEmpty()) {
            status = GateStatus.NEEDS_CLARIFICATION;
        } else {
            status = GateStatus.BLOCKED;
        }

        List<PendingDecision> pendingDecisions = new ArrayList<>();
        for (String info : semantic.getMissingTopics()) {
            pendingDecisions.add(new PendingDecision(PendingDecisionType.UNDERSTANDING_INPUT,
                    "missing." + pendingDecisions.size(), "supplement info", info, true));
        }
        for (String question : semantic.getSuggestedQuestions()) {
            pendingDecisions.add(new PendingDecision(PendingDecisionType.UNDERSTANDING_INPUT,
                    "question." + pendingDecisions.size(), "needs confirmation", question, true));
        }

        String summary = semantic.getFeedbackItems().isEmpty() ? "gate review completed"
                : String.join("; ", semantic.getFeedbackItems());
        GateReviewNote note = new GateReviewNote(status, summary,
                semantic.getFeedbackItems(), List.of("LLM semantic review"));

        // If passed and no pending decisions, add confirmation decisions for developer to explicitly confirm
        if (status == GateStatus.PASS || status == GateStatus.NEEDS_CLARIFICATION) {
            if (pendingDecisions.isEmpty() && context.getPendingDecisions().stream()
                    .noneMatch(d -> d.getType() == PendingDecisionType.CONFIRMATION)) {
                // Add standard confirmation decisions
                pendingDecisions.add(new PendingDecision(PendingDecisionType.CONFIRMATION,
                        "goal.confirm", "confirm goal", "confirm that the task goal analysis is accurate", true));
                pendingDecisions.add(new PendingDecision(PendingDecisionType.CONFIRMATION,
                        "toolset.confirm", "confirm tools", "confirm the tool set to be used", true));
                pendingDecisions.add(new PendingDecision(PendingDecisionType.CONFIRMATION,
                        "risk.confirm", "confirm risks", "confirm identified risks and accept", true));
            }
        }

        return new GateCheckResult(status, summary, semantic.isCoveragePassed() ? "gate passed" : "needs more info",
                note, pendingDecisions);
    }

    private void recordAttempt(TaskExecutionContext context, int attemptIndex, Instant timestamp,
                               GateSemanticReviewResult semantic, GateCheckResult result) {
        // Record attempt in memory
        GateReviewAttempt attempt = new GateReviewAttempt(
                attemptIndex, timestamp,
                context.getDeveloperUnderstanding(),
                null, // No rule result in v2
                semantic,
                null, // No merged result in v2
                result.getGateStatus(),
                result.getGateReviewNote() != null ? result.getGateReviewNote().getEvidence() : List.of());
        context.addGateReviewAttempt(attempt);

        // Persist to SQLite
        try {
            String json = attemptSerializer.serialize(attempt);
            MemoryFragment fragment = MemoryFragment.create(
                    MemoryType.GATE_ATTEMPT,
                    "gate attempt #" + attemptIndex,
                    json, 0.7, context.getTaskId(), "GATE_CONFIRM",
                    List.of("gate", result.getGateStatus().name()));
            memoryService.save(fragment);
        } catch (Exception e) {
            log.warn("failed to persist gate attempt: taskId={}, attemptIndex={}",
                    context.getTaskId(), attemptIndex, e);
        }
    }
}
