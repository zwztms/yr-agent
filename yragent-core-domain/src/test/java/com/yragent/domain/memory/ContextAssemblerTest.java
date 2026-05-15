package com.yragent.domain.memory;

import com.yragent.domain.stage.RoundRecord;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextAssemblerTest {

    private MemoryService memoryService;
    private ContextAssembler assembler;

    @BeforeEach
    void setUp() {
        memoryService = new MemoryService(
                new FakeMemoryRepository(),
                new PreferenceSerializer(),
                new PolicySerializer());
        assembler = new ContextAssembler(memoryService);
    }

    @Test
    void shouldRenderMinimalContextWhenEmpty() {
        TaskExecutionContext ctx = new TaskExecutionContext();
        ctx.setTaskId("test-task");
        ctx.setUserInput("test input");

        String result = assembler.renderContext(StageType.GOAL_DEFINITION, ctx, 10);
        assertTrue(result.contains("--- 以上是上下文，以下是当前任务 ---"));
    }

    @Test
    void shouldRenderPreferenceWhenSet() {
        TaskExecutionContext ctx = new TaskExecutionContext();
        ctx.setTaskId("test-task");
        ctx.setUserInput("test input");
        ctx.setUserPreference(UserPreference.defaults());
        ctx.setProjectPolicy(ProjectPolicy.defaults());

        String result = assembler.renderContext(StageType.GOAL_DEFINITION, ctx, 10);
        assertTrue(result.contains("riskTolerance=balanced"));
        assertTrue(result.contains("projectType=generic"));
    }

    @Test
    void shouldRenderMemoriesWhenPresent() {
        // Insert some memory fragments
        memoryService.save(MemoryFragment.create(
                MemoryType.DECISION, "过往决策", "选用了方案A", 0.9,
                "test-task", StageType.PLANNING.name(),
                List.of("决策")));
        memoryService.save(MemoryFragment.create(
                MemoryType.FAILURE_PATTERN, "失败记录", "上次write_file路径错误", 0.8,
                "test-task", StageType.EXECUTION.name(),
                List.of("失败")));

        TaskExecutionContext ctx = new TaskExecutionContext();
        ctx.setTaskId("test-task");
        ctx.setUserInput("创建一个文件");
        ctx.setUserPreference(UserPreference.defaults());

        String result = assembler.renderContext(StageType.PLANNING, ctx, 10);
        // Should contain the memories loaded for PLANNING stage
        assertTrue(result.contains("[相关记忆]"));
    }

    @Test
    void shouldRenderConversationHistory() {
        TaskExecutionContext ctx = new TaskExecutionContext();
        ctx.setTaskId("test-task");
        ctx.setUserInput("test");
        ctx.setUserPreference(UserPreference.defaults());
        ctx.getConversationHistory().addTurn("用户问题", "LLM回答", StageType.GOAL_DEFINITION);

        String result = assembler.renderContext(StageType.GOAL_DEFINITION, ctx, 10);
        assertTrue(result.contains("最近对话"));
        assertTrue(result.contains("用户问题"));
    }

    @Test
    void shouldRenderRoundSummaryForMultiRound() {
        TaskExecutionContext ctx = new TaskExecutionContext();
        ctx.setTaskId("test-task");
        ctx.setUserInput("test");
        ctx.setCurrentRound(1);
        ctx.setUserPreference(UserPreference.defaults());
        ctx.addRoundRecord(new RoundRecord(0, null, null, null, "上一轮完成了HelloWorld创建", false));

        String result = assembler.renderContext(StageType.PLANNING, ctx, 10);
        assertTrue(result.contains("多轮摘要"));
        assertTrue(result.contains("第1轮"));
    }

    @Test
    void shouldNotRenderEmptyHistory() {
        TaskExecutionContext ctx = new TaskExecutionContext();
        ctx.setTaskId("test-task");
        ctx.setUserInput("test");
        ctx.setUserPreference(UserPreference.defaults());
        // No history added

        String result = assembler.renderContext(StageType.GOAL_DEFINITION, ctx, 10);
        assertFalse(result.contains("最近对话"));
    }

    @Test
    void shouldRespectMaxMemoriesLimit() {
        for (int i = 0; i < 10; i++) {
            memoryService.save(MemoryFragment.create(
                    MemoryType.FAILURE_PATTERN, "标题" + i, "内容" + i, 0.5 + i * 0.05,
                    "test-task", StageType.PLANNING.name(), List.of("exp")));
        }

        TaskExecutionContext ctx = new TaskExecutionContext();
        ctx.setTaskId("test-task");
        ctx.setUserInput("test");
        ctx.setUserPreference(UserPreference.defaults());

        String result = assembler.renderContext(StageType.PLANNING, ctx, 3);
        // Count [EXPERIENCE] occurrences — should be at most 3
        long count = result.lines().filter(l -> l.contains("[EXPERIENCE]")).count();
        assertTrue(count <= 3, "should render at most 3 memories, got " + count);
    }
}
