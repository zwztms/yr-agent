package com.yragent.orchestrator;

import com.yragent.domain.gate.GateCheckResult;
import com.yragent.domain.gate.GateReviewAttempt;
import com.yragent.domain.gate.StageGateEngine;
import com.yragent.domain.memory.GateReviewAttemptSerializer;
import com.yragent.domain.memory.MemoryFragment;
import com.yragent.domain.memory.MemoryService;
import com.yragent.domain.memory.MemoryType;
import com.yragent.domain.stage.StageResult;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import com.yragent.domain.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GateConfirmStageHandler implements StageHandler {

    private static final Logger log = LoggerFactory.getLogger(GateConfirmStageHandler.class);

    private final TraceRecorder traceRecorder;
    private final StageGateEngine stageGateEngine;
    private final MemoryService memoryService;
    private final GateReviewAttemptSerializer attemptSerializer;

    public GateConfirmStageHandler(TraceRecorder traceRecorder,
                                   StageGateEngine stageGateEngine,
                                   MemoryService memoryService,
                                   GateReviewAttemptSerializer attemptSerializer) {
        this.traceRecorder = traceRecorder;
        this.stageGateEngine = stageGateEngine;
        this.memoryService = memoryService;
        this.attemptSerializer = attemptSerializer;
    }

    @Override
    public StageType support() {
        return StageType.GATE_CONFIRM;
    }

    @Override
    public StageResult handle(TaskExecutionContext context) {
        traceRecorder.recordStageStart(context.getTaskId(), support());
        // 首次进入门禁阶段时，从 SQLite 加载该任务的门禁历史。
        loadGateHistoryIfNeeded(context);
        // 门禁判断独立放在 StageGateEngine，当前处理器只负责把门禁结果接入主阶段链。
        GateCheckResult gateCheckResult = stageGateEngine.evaluate(context);
        StageResult result = new StageResult(
                support(),
                gateCheckResult.isPassed(),
                gateCheckResult.getSummary(),
                gateCheckResult.getStageSummary(),
                gateCheckResult.getGateReviewNote(),
                gateCheckResult.getPendingDecisions(),
                "developer submits understanding, risk notes and required confirmations before execution",
                gateCheckResult.isPassed() ? null : "stage gate is not passed"
        );
        traceRecorder.recordStageFinish(context.getTaskId(), support(), result.isPassed(), result.getSummary());
        return result;
    }

    // 从 SQLite 加载该任务的历史门禁记录，恢复时避免重复加载。
    private void loadGateHistoryIfNeeded(TaskExecutionContext context) {
        if (context.isGateHistoryLoaded()) {
            return;
        }
        try {
            List<MemoryFragment> fragments = memoryService.findByTypeAndTaskId(
                    MemoryType.GATE_ATTEMPT, context.getTaskId());
            for (MemoryFragment fragment : fragments) {
                GateReviewAttempt attempt = attemptSerializer.deserialize(fragment.getContent());
                context.addGateReviewAttempt(attempt);
            }
            context.setGateHistoryLoaded(true);
        } catch (Exception e) {
            log.warn("加载门禁历史失败: taskId={}", context.getTaskId(), e);
        }
    }
}
