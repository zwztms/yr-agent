package com.yragent.domain.gate;

import com.yragent.domain.gate.policy.GateReviewMergePolicy;
import com.yragent.domain.gate.step.GateSemanticReviewStep;
import com.yragent.domain.gate.step.RuleGateCheckStep;
import com.yragent.domain.memory.GateReviewAttemptSerializer;
import com.yragent.domain.memory.MemoryFragment;
import com.yragent.domain.memory.MemoryService;
import com.yragent.domain.memory.MemoryType;
import com.yragent.domain.stage.TaskExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class StageGateEngine {

    private static final Logger log = LoggerFactory.getLogger(StageGateEngine.class);

    private final RuleGateCheckStep ruleGateCheckStep;
    private final GateSemanticReviewStep gateSemanticReviewStep;
    private final GateReviewMergePolicy gateReviewMergePolicy;
    private final GateCheckResultBuilder gateCheckResultBuilder;
    private final MemoryService memoryService;
    private final GateReviewAttemptSerializer attemptSerializer;

    public StageGateEngine(RuleGateCheckStep ruleGateCheckStep,
                           GateSemanticReviewStep gateSemanticReviewStep,
                           GateReviewMergePolicy gateReviewMergePolicy,
                           GateCheckResultBuilder gateCheckResultBuilder,
                           MemoryService memoryService,
                           GateReviewAttemptSerializer attemptSerializer) {
        this.ruleGateCheckStep = ruleGateCheckStep;
        this.gateSemanticReviewStep = gateSemanticReviewStep;
        this.gateReviewMergePolicy = gateReviewMergePolicy;
        this.gateCheckResultBuilder = gateCheckResultBuilder;
        this.memoryService = memoryService;
        this.attemptSerializer = attemptSerializer;
    }

    public GateCheckResult evaluate(TaskExecutionContext context) {
        int attemptIndex = context.getGateReviewAttempts().size() + 1;
        Instant timestamp = Instant.now();

        RuleGateReviewResult ruleResult = ruleGateCheckStep.check(context);
        GateSemanticReviewResult semanticResult;
        MergedGateReviewResult mergedResult;
        GateCheckResult checkResult;

        if (ruleResult.getGateStatus() == GateStatus.BLOCKED) {
            semanticResult = GateSemanticReviewResult.notApplied();
            checkResult = gateCheckResultBuilder.buildFromRule(ruleResult);
            mergedResult = new MergedGateReviewResult(
                    ruleResult.getGateStatus(),
                    ruleResult.getStageSummary(),
                    ruleResult.getPendingDecisions(),
                    ruleResult.getFeedbackItems(),
                    false,
                    false
            );
        } else {
            semanticResult = gateSemanticReviewStep.review(context, ruleResult);
            mergedResult = gateReviewMergePolicy.merge(ruleResult, semanticResult);
            checkResult = gateCheckResultBuilder.build(mergedResult);
        }

        // 记录本轮门禁 attempt 到内存，保存完整快照供后续审计。
        GateReviewAttempt attempt = new GateReviewAttempt(
                attemptIndex,
                timestamp,
                context.getDeveloperUnderstanding(),
                ruleResult,
                semanticResult,
                mergedResult,
                checkResult.getGateStatus(),
                List.of()
        );
        context.addGateReviewAttempt(attempt);

        // 同时持久化到 SQLite，供任务恢复时加载。
        persistAttempt(context.getTaskId(), attempt, attemptIndex);

        return checkResult;
    }

    private void persistAttempt(String taskId, GateReviewAttempt attempt, int attemptIndex) {
        try {
            String json = attemptSerializer.serialize(attempt);
            MemoryFragment fragment = MemoryFragment.create(
                    MemoryType.GATE_ATTEMPT,
                    "门禁第" + attemptIndex + "轮",
                    json,
                    0.7,
                    taskId,
                    "GATE_CONFIRM",
                    List.of("门禁", attempt.getFinalStatus().name())
            );
            memoryService.save(fragment);
        } catch (Exception e) {
            // 持久化失败不阻断门禁流程。
            log.warn("持久化门禁尝试记录失败: taskId={}, attemptIndex={}", taskId, attemptIndex, e);
        }
    }
}
