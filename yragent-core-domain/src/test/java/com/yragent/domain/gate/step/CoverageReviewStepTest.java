package com.yragent.domain.gate.step;

import com.yragent.domain.gate.checklist.GateCheckItem;
import com.yragent.domain.gate.checklist.ItemCoverage;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.stage.StageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoverageReviewStepTest {

    private static final String VALID_LLM_RESPONSE = """
            [
              {"itemId":"PL-2","status":"covered","evidence":"用户明确提到write_file","suggestion":""},
              {"itemId":"PL-3","status":"partial","evidence":"用户提到风险但不够具体","suggestion":"文件冲突时怎么办？"}
            ]""";

    @Test
    void shouldParseLlmResponseCorrectly() {
        LlmClient llm = new LlmClient() {
            @Override public String chatCompletion(String p) { return "{}"; }
            @Override public String structuredCompletion(String p, String s) { return VALID_LLM_RESPONSE; }
        };

        List<GateCheckItem> items = List.of(
                new GateCheckItem("PL-2", "工具边界", "用什么工具？", List.of("write_file")),
                new GateCheckItem("PL-3", "风险识别", "有风险吗？", List.of("风险"))
        );

        CoverageReviewStep step = new CoverageReviewStep(llm);
        List<ItemCoverage> result = step.review(items, "我会用write_file创建文件",
                StageType.PLANNING, "文件任务", "创建HelloWorld");

        assertEquals(2, result.size());
        assertTrue(result.get(0).isCovered());
        assertEquals("partial", result.get(1).status());
    }

    @Test
    void shouldFallbackWhenLlmFails() {
        LlmClient llm = new LlmClient() {
            @Override public String chatCompletion(String p) { return "{}"; }
            @Override public String structuredCompletion(String p, String s) {
                throw new RuntimeException("API error");
            }
        };

        CoverageReviewStep step = new CoverageReviewStep(llm);
        List<ItemCoverage> result = step.review(
                List.of(new GateCheckItem("PL-2", "工具", "用什么？", List.of("write_file"))),
                "write_file", StageType.PLANNING, "t", "p");

        assertEquals(1, result.size());
        assertEquals("partial", result.get(0).status());
        assertTrue(result.get(0).evidence().contains("fallback"));
    }

    @Test
    void shouldFallbackWhenInvalidJson() {
        LlmClient llm = new LlmClient() {
            @Override public String chatCompletion(String p) { return "{}"; }
            @Override public String structuredCompletion(String p, String s) { return "not json"; }
        };

        CoverageReviewStep step = new CoverageReviewStep(llm);
        List<ItemCoverage> result = step.review(
                List.of(new GateCheckItem("PL-2", "工具", "用什么？", List.of("write_file"))),
                "write_file", StageType.PLANNING, "t", "p");

        assertEquals(1, result.size());
    }

    @Test
    void shouldHandleEmptyChecklist() {
        LlmClient llm = new LlmClient() {
            @Override public String chatCompletion(String p) { return "{}"; }
            @Override public String structuredCompletion(String p, String s) { return VALID_LLM_RESPONSE; }
        };
        CoverageReviewStep step = new CoverageReviewStep(llm);
        List<ItemCoverage> result = step.review(List.of(), "x", StageType.PLANNING, "t", "p");
        assertTrue(result.isEmpty());
    }
}
