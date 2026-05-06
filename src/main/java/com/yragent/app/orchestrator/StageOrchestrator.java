package com.yragent.app.orchestrator;

import com.yragent.domain.memory.MemoryFragment;
import com.yragent.domain.memory.MemoryService;
import com.yragent.domain.memory.MemoryType;
import com.yragent.domain.memory.TaskStateSnapshot;
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

    private final Map<StageType, StageHandler> handlers = new EnumMap<>(StageType.class);
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;

    public StageOrchestrator(List<StageHandler> stageHandlers, MemoryService memoryService) {
        for (StageHandler handler : stageHandlers) {
            handlers.put(handler.support(), handler);
        }
        this.memoryService = memoryService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public TaskExecutionContext start(String userInput) {
        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskId(UUID.randomUUID().toString());
        context.setUserInput(userInput);
        return runStages(context, StageType.GOAL_DEFINITION);
    }

    public TaskExecutionContext resumeFrom(TaskExecutionContext context, StageType startStage) {
        return runStages(context, startStage);
    }

    private TaskExecutionContext runStages(TaskExecutionContext context, StageType startStage) {
        // 当前版本按固定阶段顺序推进，后续再扩为可恢复、可回退的状态机。
        boolean started = false;
        for (StageType stageType : StageType.values()) {
            if (!started && stageType != startStage) {
                continue;
            }
            started = true;

            context.setCurrentStage(stageType);
            StageHandler handler = handlers.get(stageType);
            if (handler == null) {
                throw new IllegalStateException("Missing stage handler: " + stageType);
            }

            StageResult result = handler.handle(context);
            context.addStageNote(stageType + ": " + result.getSummary());
            // 阶段结果会被回写到上下文，供 CLI 输出和后续阶段读取。
            context.replacePendingDecisions(result.getPendingDecisions());
            context.setCurrentStageSummary(result.getStageSummary());
            context.setGateReviewNote(result.getGateReviewNote());
            context.setNextAction(result.getNextAction());
            context.setFailureReason(result.getFailureReason());
            // 每个阶段完成后保存任务状态快照，供中断恢复使用。
            saveTaskSnapshot(context, stageType);
            // 某阶段未通过时立即停线，当前版本还没有恢复和重试机制。
            if (!result.isPassed()) {
                break;
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
}
