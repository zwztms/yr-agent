package com.yragent.orchestrator;

import com.yragent.domain.memory.MemoryFragment;
import com.yragent.domain.memory.MemoryService;
import com.yragent.domain.memory.MemoryType;
import com.yragent.domain.memory.TaskStateSnapshot;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.stage.RoundRecord;
import com.yragent.domain.stage.StageResult;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class StageOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(StageOrchestrator.class);
    private static final int MAX_ROUNDS = 10;

    private final Map<StageType, StageHandler> handlers = new EnumMap<>(StageType.class);
    private final MemoryService memoryService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public StageOrchestrator(List<StageHandler> stageHandlers, MemoryService memoryService,
                             LlmClient llmClient) {
        for (StageHandler handler : stageHandlers) {
            handlers.put(handler.support(), handler);
        }
        this.memoryService = memoryService;
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public TaskExecutionContext start(String userInput) {
        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskId(UUID.randomUUID().toString());
        context.setUserInput(userInput);
        context.setCurrentRound(0);
        return runStages(context, StageType.GOAL_DEFINITION);
    }

    public TaskExecutionContext resumeFrom(TaskExecutionContext context, StageType startStage) {
        return runStages(context, startStage);
    }

    private TaskExecutionContext runStages(TaskExecutionContext context, StageType startStage) {
        boolean started = false;
        int roundStartCount = 0;

        for (StageType stageType : StageType.values()) {
            // 多轮执行：第0轮之后跳过目标定义和目标确认阶段
            if (context.getCurrentRound() > 0) {
                if (stageType == StageType.GOAL_DEFINITION || stageType == StageType.CLARIFY_GOAL) {
                    continue;
                }
            }

            if (!started && stageType != startStage) {
                continue;
            }
            started = true;

            // 新轮次首次进入 PLANNING 时记录轮次开始
            if (stageType == StageType.PLANNING && context.getCurrentRound() > 0) {
                roundStartCount++;
                log.info("Round {} starting", context.getCurrentRound());
            }

            context.setCurrentStage(stageType);
            StageHandler handler = handlers.get(stageType);
            if (handler == null) {
                // CLARIFY_GOAL 可能没有注册 Handler，优雅跳过
                if (stageType == StageType.CLARIFY_GOAL) {
                    log.debug("No handler for CLARIFY_GOAL, skipping");
                    continue;
                }
                throw new IllegalStateException("Missing stage handler: " + stageType);
            }

            StageResult result = handler.handle(context);
            context.addStageNote(stageType + ": " + result.getSummary());
            context.replacePendingDecisions(result.getPendingDecisions());
            context.setCurrentStageSummary(result.getStageSummary());
            context.setGateReviewNote(result.getGateReviewNote());
            context.setNextAction(result.getNextAction());
            context.setFailureReason(result.getFailureReason());
            saveTaskSnapshot(context, stageType);
            memoryService.captureStageOutput(context, stageType, result);
            context.getConversationHistory().compressIfNeeded(llmClient, 20);
            // 某阶段未通过时立即停线。
            if (!result.isPassed()) {
                break;
            }
        }

        // REVIEW 阶段后检查项目是否完成，未完成则进入下一轮
        if (context.getCurrentStage() == StageType.REVIEW && !context.isCompleted()) {
            int nextRound = context.getCurrentRound() + 1;
            if (nextRound < MAX_ROUNDS) {
                log.info("Project not complete, entering round {}", nextRound);
                // 保存当前轮次记录
                saveRoundRecord(context);
                // 进入下一轮，从 PLANNING 重新开始
                context.setCurrentRound(nextRound);
                return runStages(context, StageType.PLANNING);
            } else {
                log.warn("Reached max rounds ({}), forcing completion", MAX_ROUNDS);
                context.setCompleted(true);
            }
        }

        return context;
    }

    private void saveTaskSnapshot(TaskExecutionContext context, StageType stageType) {
        try {
            TaskStateSnapshot snapshot = TaskStateSnapshot.from(context);
            String json = objectMapper.writeValueAsString(snapshot);
            MemoryFragment fragment = MemoryFragment.create(
                    MemoryType.TASK_STATE,
                    "阶段完成: " + stageType,
                    json,
                    1.0,
                    context.getTaskId(),
                    stageType.name(),
                    List.of("快照", stageType.name())
            );
            memoryService.save(fragment);
        } catch (Exception e) {
            // 快照保存失败不影响阶段推进。
            log.warn("保存任务状态快照失败: taskId={}, stage={}", context.getTaskId(), stageType, e);
        }
    }

    private void saveRoundRecord(TaskExecutionContext context) {
        String reviewSummary = "REVIEW: " + (context.getCurrentStageSummary() != null
                ? context.getCurrentStageSummary() : "无");
        RoundRecord record = new RoundRecord(
                context.getCurrentRound(),
                context.getPlanDocument(),
                context.getExecutionResult(),
                context.getVerificationResult(),
                reviewSummary,
                context.isCompleted()
        );
        context.addRoundRecord(record);
    }
}
