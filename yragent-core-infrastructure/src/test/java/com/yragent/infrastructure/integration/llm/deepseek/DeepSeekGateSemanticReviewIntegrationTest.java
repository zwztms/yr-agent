package com.yragent.infrastructure.integration.llm.deepseek;

import com.yragent.domain.gate.checklist.GateCheckItem;
import com.yragent.domain.gate.checklist.ItemCoverage;
import com.yragent.domain.gate.checklist.StageChecklistRegistry;
import com.yragent.domain.gate.step.CoverageReviewStep;
import com.yragent.domain.stage.StageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DeepSeekGateSemanticReviewIntegrationTest {

    @Test
    void shouldCallRealDeepSeekAndScoreChecklist() {
        assumeTrue(hasDeepSeekApiKey(), "Missing DEEPSEEK_API_KEY for DeepSeek integration test");

        DeepSeekCompatibleLlmClient llmClient = new DeepSeekCompatibleLlmClient(
                "https://api.deepseek.com", "deepseek-chat", "", "/v1/chat/completions");
        CoverageReviewStep step = new CoverageReviewStep(llmClient);

        List<GateCheckItem> items = StageChecklistRegistry.forStage(StageType.PLANNING);
        List<ItemCoverage> result = step.review(items,
                "我会用write_file工具创建HelloWorld.java文件，主要风险是文件可能已存在需要处理",
                StageType.PLANNING, "文件操作任务", "创建HelloWorld.java文件");

        assertFalse(result.isEmpty());
        assertTrue(result.size() >= 3);
        // at least one item should be covered
        assertTrue(result.stream().anyMatch(ItemCoverage::isCovered));
    }

    private boolean hasDeepSeekApiKey() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        return apiKey != null && !apiKey.isBlank();
    }
}
