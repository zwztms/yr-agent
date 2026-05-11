package com.yragent.domain.execution;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionPlanSerializerTest {

    private final ExecutionPlanSerializer serializer = new ExecutionPlanSerializer();

    @Test
    void shouldSerializeAndDeserializeFullPlan() {
        ExecutionPlan original = new ExecutionPlan(
                List.of(
                        new ExecutionPlan.ExecutionStep(1, "write_file",
                                Map.of("path", "Hello.java", "content", "class Hello {}"),
                                "创建主类文件"),
                        new ExecutionPlan.ExecutionStep(2, "run_command",
                                Map.of("command", "javac Hello.java", "workingDir", "workspace"),
                                "编译 Java 文件")
                ),
                "先生成文件再编译"
        );

        String json = serializer.serialize(original);
        ExecutionPlan restored = serializer.deserialize(json);

        assertEquals(2, restored.getSteps().size());
        assertEquals("先生成文件再编译", restored.getRationale());
        assertEquals("write_file", restored.getSteps().get(0).tool());
        assertEquals("创建主类文件", restored.getSteps().get(0).reason());
        assertEquals("Hello.java", restored.getSteps().get(0).params().get("path"));
        assertEquals("run_command", restored.getSteps().get(1).tool());
    }

    @Test
    void shouldHandleEmptySteps() {
        ExecutionPlan original = new ExecutionPlan(List.of(), "无操作");

        String json = serializer.serialize(original);
        ExecutionPlan restored = serializer.deserialize(json);

        assertTrue(restored.getSteps().isEmpty());
        assertEquals("无操作", restored.getRationale());
    }

    @Test
    void shouldExtractJsonFromMarkdownBlock() {
        String llmOutput = """
                以下是执行计划：
                ```json
                {
                  "rationale": "test",
                  "steps": [
                    {"index": 1, "tool": "list_dir", "params": {"path": "."}, "reason": "查看目录"}
                  ]
                }
                ```
                """;

        ExecutionPlan plan = serializer.deserializeFromLlmOutput(llmOutput);
        assertNotNull(plan);
        assertEquals("test", plan.getRationale());
        assertEquals(1, plan.getSteps().size());
        assertEquals("list_dir", plan.getSteps().get(0).tool());
    }

    @Test
    void shouldExtractBareJson() {
        String llmOutput = """
                {"rationale": "bare json test", "steps": []}""";

        ExecutionPlan plan = serializer.deserializeFromLlmOutput(llmOutput);
        assertNotNull(plan);
        assertEquals("bare json test", plan.getRationale());
        assertTrue(plan.getSteps().isEmpty());
    }

    @Test
    void shouldHandleInvalidLlmOutput() {
        assertThrows(IllegalStateException.class, () -> {
            serializer.deserializeFromLlmOutput("这不是 JSON");
        });
    }
}
