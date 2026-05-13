package com.yragent.domain.gate;

import com.yragent.domain.gate.checklist.*;
import com.yragent.domain.gate.step.CoverageReviewStep;
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

    private final CoverageReviewStep coverageReviewStep;
    private final MemoryService memoryService;
    private final GateReviewAttemptSerializer attemptSerializer;

    public StageGateEngine(CoverageReviewStep coverageReviewStep,
                           MemoryService memoryService,
                           GateReviewAttemptSerializer attemptSerializer) {
        this.coverageReviewStep = coverageReviewStep;
        this.memoryService = memoryService;
        this.attemptSerializer = attemptSerializer;
    }

    public GateCheckResult evaluate(TaskExecutionContext context) {
        int attemptIndex = context.getGateReviewAttempts().size() + 1;
        Instant timestamp = Instant.now();

        var goal = context.getGoalAnalysis();
        var plan = context.getPlanDocument();
        boolean hasGoal = goal != null && !goal.goals().isEmpty();
        boolean hasPlan = plan != null && !plan.getSteps().isEmpty();
        if (context.getApproachPlan() != null) hasPlan = true;

        if (!hasGoal && !hasPlan) {
            var result = blockEmptyUpstream();
            recordAttempt(context, attemptIndex, timestamp, null, result);
            return result;
        }

        // Get developer input
        String userInput = extractUserInput(context);

        // Hard fallback: empty input → BLOCKED
        if (userInput == null || userInput.trim().length() < 10) {
            var result = blockEmptyInput();
            recordAttempt(context, attemptIndex, timestamp, null, result);
            return result;
        }

        // Load checklist for current stage
        List<GateCheckItem> items = StageChecklistRegistry.forStage(context.getCurrentStage());

        // LLM batch scoring
        String goalSummary = goal != null ? goal.taskType() + ": " + String.join(";", goal.goals()) : "无";
        String planSummary = plan != null ? plan.getOverview() : "无";
        List<ItemCoverage> scores = coverageReviewStep.review(
                items, userInput, context.getCurrentStage(), goalSummary, planSummary);

        // Three-layer cross-validation
        scores = applyCrossValidation(items, scores, userInput, context);

        // Verdict: all covered → PASS
        boolean allCovered = scores.stream().allMatch(ItemCoverage::isCovered);
        String summary = allCovered ? "all items covered" : buildMissingSummary(scores);

        List<PendingDecision> decisions = new ArrayList<>();
        for (ItemCoverage s : scores) {
            if (!s.isCovered()) {
                decisions.add(new PendingDecision(PendingDecisionType.UNDERSTANDING_INPUT,
                        s.itemId(), "supplement info", s.suggestion(), true));
            }
        }

        GateStatus status = allCovered ? GateStatus.PASS
                : decisions.isEmpty() ? GateStatus.PASS : GateStatus.NEEDS_CLARIFICATION;

        GateReviewNote note = new GateReviewNote(status, summary,
                scores.stream().map(s -> s.itemId() + ":" + s.status()).toList(),
                List.of("checklist v2.1", "EvidenceValidator", "DimensionChecker", "RoundConsistencyChecker"));

        var result = new GateCheckResult(status, summary,
                allCovered ? "gate passed" : "needs more info", note, decisions);
        recordAttempt(context, attemptIndex, timestamp, null, result);
        return result;
    }

    private String extractUserInput(TaskExecutionContext ctx) {
        if (ctx.getDeveloperUnderstanding() != null) {
            var dev = ctx.getDeveloperUnderstanding();
            String s = dev.getStageSummary();
            String r = dev.getRiskSummary();
            String part1 = s != null && !s.isBlank() ? s : "";
            String part2 = r != null && !r.isBlank() ? r : "";
            if (!part1.isEmpty() && !part2.isEmpty()) return part1 + "\n" + part2;
            if (!part1.isEmpty()) return part1;
            if (!part2.isEmpty()) return part2;
        }
        return ctx.getCurrentStageSummary();
    }

    private List<ItemCoverage> applyCrossValidation(List<GateCheckItem> items,
            List<ItemCoverage> scores, String userInput, TaskExecutionContext ctx) {
        List<ItemCoverage> validated = new ArrayList<>();

        // Load previous round scores for round consistency check
        List<ItemCoverage> prevScores = loadPrevScores(ctx);
        String prevInput = null;

        for (int i = 0; i < scores.size(); i++) {
            ItemCoverage score = scores.get(i);
            GateCheckItem item = i < items.size() ? items.get(i) : null;
            String status = score.status();

            // Only validate LLM-claimed "covered" — if LLM already says partial/missing, trust it
            if (score.isCovered() && item != null) {
                // Layer 1: Evidence validation
                if (!EvidenceValidator.isEvidenceValid(score.evidence(), userInput)) {
                    status = ItemCoverage.PARTIAL;
                    log.debug("EvidenceValidator downgraded {}: evidence not found in user input", item.id());
                }
                // Layer 2: Dimension check
                else if (!DimensionChecker.isDimensionCovered(item, userInput)) {
                    status = ItemCoverage.PARTIAL;
                    log.debug("DimensionChecker downgraded {}: dimension trigger failed", item.id());
                }
            }

            validated.add(new ItemCoverage(score.itemId(), status, score.evidence(), score.suggestion()));
        }

        // Layer 3: Round consistency
        if (prevScores != null && !prevScores.isEmpty() && prevInput != null) {
            validated = RoundConsistencyChecker.check(prevScores, validated, prevInput, userInput);
        }

        return validated;
    }

    private List<ItemCoverage> loadPrevScores(TaskExecutionContext ctx) {
        var attempts = ctx.getGateReviewAttempts();
        if (attempts.isEmpty()) return List.of();
        // For v2.1: return empty for now — historical attempts don't have ItemCoverage format
        return List.of();
    }

    private String buildMissingSummary(List<ItemCoverage> scores) {
        List<String> missing = scores.stream()
                .filter(s -> !s.isCovered())
                .map(s -> s.itemId() + ": " + s.suggestion())
                .toList();
        return "items need attention: " + String.join("; ", missing);
    }

    private GateCheckResult blockEmptyUpstream() {
        var decisions = List.of(new PendingDecision(PendingDecisionType.UNDERSTANDING_INPUT,
                "design.input", "supplement design info",
                "goal analysis and plan are both empty, please complete upstream stages first", true));
        var note = new GateReviewNote(GateStatus.BLOCKED,
                "missing goal and plan", List.of("complete upstream stages"), List.of("upstream validation"));
        return new GateCheckResult(GateStatus.BLOCKED, "missing upstream output", null, note, decisions);
    }

    private GateCheckResult blockEmptyInput() {
        var decisions = List.of(new PendingDecision(PendingDecisionType.UNDERSTANDING_INPUT,
                "input.required", "gate input required",
                "请用你自己的话描述对当前阶段设计的理解（至少10字），不要只写'可以''好的'", true));
        var note = new GateReviewNote(GateStatus.BLOCKED, "empty developer input",
                List.of("input below 10 chars"), List.of("hard fallback v2.1"));
        return new GateCheckResult(GateStatus.BLOCKED, "developer input is empty or too short", null, note, decisions);
    }

    private void recordAttempt(TaskExecutionContext context, int attemptIndex, Instant timestamp,
                               Object unused, GateCheckResult result) {
        GateReviewAttempt attempt = new GateReviewAttempt(
                attemptIndex, timestamp,
                context.getDeveloperUnderstanding(),
                null, null, null,
                result.getGateStatus(),
                result.getGateReviewNote() != null ? result.getGateReviewNote().getEvidence() : List.of());
        context.addGateReviewAttempt(attempt);

        try {
            String json = attemptSerializer.serialize(attempt);
            MemoryFragment fragment = MemoryFragment.create(
                    MemoryType.GATE_ATTEMPT, "gate attempt #" + attemptIndex,
                    json, 0.7, context.getTaskId(), "GATE_CONFIRM",
                    List.of("gate", result.getGateStatus().name()));
            memoryService.save(fragment);
        } catch (Exception e) {
            log.warn("failed to persist gate attempt: taskId={}, attemptIndex={}",
                    context.getTaskId(), attemptIndex, e);
        }
    }
}
