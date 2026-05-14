package com.yragent.orchestrator;

import com.yragent.domain.execution.ExecutionPlan;
import com.yragent.domain.execution.ExecutionPlanSerializer;
import com.yragent.domain.execution.ExecutionResult;
import com.yragent.domain.goal.GoalAnalysis;
import com.yragent.domain.memory.ContextAssembler;
import com.yragent.domain.memory.MemoryFragment;
import com.yragent.domain.memory.MemoryRepository;
import com.yragent.domain.memory.MemoryService;
import com.yragent.domain.memory.MemoryType;
import com.yragent.domain.memory.MemoryZone;
import com.yragent.domain.memory.PolicySerializer;
import com.yragent.domain.memory.PreferenceSerializer;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.planning.ApproachPlan;
import com.yragent.domain.stage.StageResult;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import com.yragent.domain.tool.ToolCapability;
import com.yragent.domain.tool.ToolExecutor;
import com.yragent.domain.tool.ToolExecutor.ToolCall;
import com.yragent.domain.tool.ToolExecutor.ToolExecutionResult;
import com.yragent.tool.ToolRegistry;
import com.yragent.domain.trace.TraceRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionStageHandlerTest {

    private ExecutionStageHandler handler;
    private StubToolExecutor toolExecutor;
    private ExecutionPlanSerializer planSerializer;
    private StubToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        toolExecutor = new StubToolExecutor();
        planSerializer = new ExecutionPlanSerializer();
        toolRegistry = new StubToolRegistry();
    }

    private ExecutionStageHandler createHandler(LlmClient llmClient) {
        MemoryRepository repo = new MemoryRepository() {
            private final java.util.Map<String, MemoryFragment> store = new java.util.concurrent.ConcurrentHashMap<>();
            @Override public void save(MemoryFragment f) { store.put(f.getId(), f); }
            @Override public java.util.Optional<MemoryFragment> findById(String id) { return java.util.Optional.ofNullable(store.get(id)); }
            @Override public void update(MemoryFragment f) { store.put(f.getId(), f); }
            @Override public void deleteById(String id) { store.remove(id); }
            @Override public java.util.List<MemoryFragment> findByType(MemoryType t, int l) { return java.util.List.of(); }
            @Override public java.util.List<MemoryFragment> findByTaskId(String id) { return java.util.List.of(); }
            @Override public java.util.List<MemoryFragment> findByTypeAndTaskId(MemoryType t, String id) { return java.util.List.of(); }
            @Override public java.util.List<MemoryFragment> searchByKeyword(String k, MemoryType t, int l) { return java.util.List.of(); }
            @Override public int deleteOlderThan(int d) { return 0; }
            @Override public java.util.List<MemoryFragment> findByZone(MemoryZone z, int l) { return java.util.List.of(); }
            @Override public java.util.List<MemoryFragment> findByZoneAndTaskId(MemoryZone z, String id) { return java.util.List.of(); }
            @Override public java.util.List<MemoryFragment> searchFts(String q, MemoryZone z, int l) { return java.util.List.of(); }
            @Override public java.util.List<MemoryFragment> searchFts(String q, int l) { return java.util.List.of(); }
        };
        MemoryService memoryService = new MemoryService(repo, new PreferenceSerializer(), new PolicySerializer());
        ContextAssembler contextAssembler = new ContextAssembler(memoryService);
        return new ExecutionStageHandler(
                new TraceRecorder(),
                llmClient,
                toolExecutor,
                planSerializer,
                toolRegistry,
                contextAssembler
        );
    }

    @Test
    void shouldIncludeRequiredParamsInPrompt() {
        // 使用一个会抛异常的 LLM 客户端，只测 prompt 构建
        TaskExecutionContext context = new TaskExecutionContext();
        context.setUserInput("测试任务");

        // 通过反射或直接调用 buildExecutionPrompt 是不可行的（private），
        // 改为通过 handle() 间接验证：prompt 构建失败不影响，检查 LLM 调用的 prompt
        // 实际用 FixedResponseLlmClient 记录 prompt
        PromptCapturingLlmClient llmClient = new PromptCapturingLlmClient(
                "{\"rationale\":\"test\",\"steps\":[]}");

        handler = createHandler(llmClient);
        handler.handle(context);

        String prompt = llmClient.getLastPrompt();
        assertNotNull(prompt);
        assertTrue(prompt.contains("write_file"), "prompt 应包含 write_file 工具名");
        assertTrue(prompt.contains("content"), "prompt 应包含 content 参数提示");
        assertTrue(prompt.contains("read_file"), "prompt 应包含 read_file 工具名");
        assertTrue(prompt.contains("path"), "prompt 应包含 path 参数提示");
    }

    @Test
    void shouldReturnPassedTrueWhenStepsFail() {
        toolExecutor.setFailNext(true);

        String planJson = """
                {
                  "rationale": "测试执行",
                  "steps": [
                    {"index": 1, "tool": "write_file", "params": {"path": "test.txt", "content": "hello"}, "reason": "创建文件"}
                  ]
                }
                """;

        handler = createHandler(new FixedResponseLlmClient(planJson));
        TaskExecutionContext context = new TaskExecutionContext();
        context.setUserInput("创建 test.txt");
        StageResult result = handler.handle(context);

        assertTrue(result.isPassed(), "EXECUTION 应始终返回 passed=true");
        assertNotNull(result.getFailureReason(), "失败时应设置 failureReason");
        assertEquals("部分执行步骤失败", result.getFailureReason());
        assertNotNull(result.getNextAction());
        assertTrue(result.getNextAction().contains("VERIFICATION"));
    }

    @Test
    void shouldReturnPassedTrueWhenAllStepsSucceed() {
        toolExecutor.setFailNext(false);

        String planJson = """
                {
                  "rationale": "测试执行",
                  "steps": [
                    {"index": 1, "tool": "write_file", "params": {"path": "test.txt", "content": "hello"}, "reason": "创建文件"}
                  ]
                }
                """;

        handler = createHandler(new FixedResponseLlmClient(planJson));
        TaskExecutionContext context = new TaskExecutionContext();
        context.setUserInput("创建 test.txt");
        StageResult result = handler.handle(context);

        assertTrue(result.isPassed(), "全部步骤成功时 passed 应为 true");
        assertNull(result.getFailureReason(), "全部成功时 failureReason 应为 null");
    }

    @Test
    void shouldIncludeGoalAnalysisAndApproachPlanInPrompt() {
        PromptCapturingLlmClient llmClient = new PromptCapturingLlmClient(
                "{\"rationale\":\"test\",\"steps\":[]}");

        handler = createHandler(llmClient);
        TaskExecutionContext context = new TaskExecutionContext();
        context.setUserInput("分析项目文件");
        context.setGoalAnalysis(new GoalAnalysis(
                "file_analysis", List.of("分析文件"), List.of("只读"), List.of("输出报告"),
                List.of(), "medium", false));
        context.setApproachPlan(new ApproachPlan(
                "使用 read_file 读取所有文件", List.of("read_file"), List.of("文件不存在"), "low"));

        handler.handle(context);

        String prompt = llmClient.getLastPrompt();
        assertTrue(prompt.contains("file_analysis"), "prompt 应包含 taskType");
        assertTrue(prompt.contains("分析文件"), "prompt 应包含 goals");
        assertTrue(prompt.contains("只读"), "prompt 应包含 constraints");
        assertTrue(prompt.contains("read_file"), "prompt 应包含 recommendedTools");
    }

    // ---- 内部桩类 ----

    private static class StubToolExecutor implements ToolExecutor {
        private boolean failNext = false;

        void setFailNext(boolean failNext) {
            this.failNext = failNext;
        }

        @Override
        public ToolExecutionResult execute(ToolCall call) {
            if (failNext) {
                return new ToolExecutionResult(call.tool(), false, "", "模拟执行失败");
            }
            return new ToolExecutionResult(call.tool(), true, "模拟执行成功", "");
        }
    }

    private static class StubToolRegistry extends ToolRegistry {
        StubToolRegistry() {
            super(null);
        }

        @Override
        public List<ToolCapability> listAll() {
            return List.of(
                    new ToolCapability("read_file", "读取文件内容",
                            com.yragent.domain.tool.ToolRiskLevel.READ_ONLY),
                    new ToolCapability("write_file", "写入文件内容",
                            com.yragent.domain.tool.ToolRiskLevel.MUTATING),
                    new ToolCapability("list_dir", "列出目录内容",
                            com.yragent.domain.tool.ToolRiskLevel.READ_ONLY),
                    new ToolCapability("run_command", "执行 Shell 命令",
                            com.yragent.domain.tool.ToolRiskLevel.DANGEROUS)
            );
        }
    }

    private static class FixedResponseLlmClient implements LlmClient {
        private final String response;

        FixedResponseLlmClient(String response) {
            this.response = response;
        }

        @Override
        public String chatCompletion(String prompt) {
            return response;
        }

        @Override
        public String chatCompletion(List<Map<String, String>> messages) {
            return response;
        }

        @Override
        public String structuredCompletion(String prompt, String schema) {
            return response;
        }

        @Override
        public String structuredCompletion(List<Map<String, String>> messages, String schema) {
            return response;
        }
    }

    private static class PromptCapturingLlmClient implements LlmClient {
        private final String response;
        private String lastPrompt;

        PromptCapturingLlmClient(String response) {
            this.response = response;
        }

        @Override
        public String chatCompletion(String prompt) {
            this.lastPrompt = prompt;
            return response;
        }

        @Override
        public String chatCompletion(List<Map<String, String>> messages) {
            this.lastPrompt = messages.toString();
            return response;
        }

        @Override
        public String structuredCompletion(String prompt, String schema) {
            return response;
        }

        @Override
        public String structuredCompletion(List<Map<String, String>> messages, String schema) {
            return response;
        }

        String getLastPrompt() {
            return lastPrompt;
        }
    }
}
