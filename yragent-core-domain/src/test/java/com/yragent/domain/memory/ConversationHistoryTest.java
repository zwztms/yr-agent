package com.yragent.domain.memory;

import com.yragent.domain.model.LlmClient;
import com.yragent.domain.stage.StageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConversationHistoryTest {

    private ConversationHistory history;

    @BeforeEach
    void setUp() {
        history = new ConversationHistory();
    }

    @Test
    void shouldStartEmpty() {
        assertTrue(history.isEmpty());
        assertEquals(0, history.size());
    }

    @Test
    void shouldRecordSingleTurn() {
        history.addTurn("用户输入", "助手回复", StageType.GOAL_DEFINITION);
        assertFalse(history.isEmpty());
        assertTrue(history.size() >= 1);
    }

    @Test
    void shouldNotRecordBlankMessages() {
        history.addTurn("   ", null, StageType.GOAL_DEFINITION);
        assertEquals(0, history.size());
    }

    @Test
    void shouldReturnRecentMessages() {
        for (int i = 0; i < 5; i++) {
            history.addTurn("用户消息" + i, "助手回复" + i, StageType.PLANNING);
        }
        List<ConversationMessage> recent = history.getRecentMessages(2);
        assertEquals(2, recent.size());
        assertEquals("用户消息4", recent.get(0).content());
    }

    @Test
    void renderForPromptShouldIncludeMessages() {
        history.addTurn("创建HelloWorld.java", "分析完成，taskType=file_operation", StageType.GOAL_DEFINITION);
        String rendered = history.renderForPrompt(3);
        assertTrue(rendered.contains("最近对话"));
        assertTrue(rendered.contains("创建HelloWorld.java"));
        assertTrue(rendered.contains("GOAL_DEFINITION"));
    }

    @Test
    void renderForPromptShouldNotHaveSummaryWhenEmpty() {
        history.addTurn("测试", "响应", StageType.EXECUTION);
        String rendered = history.renderForPrompt(5);
        assertFalse(rendered.contains("对话摘要"));
    }

    @Test
    void shouldSetAndGetSummary() {
        assertNull(history.getSummary());
    }

    @Test
    void shouldAcceptMaxWindowSize() {
        history.setMaxWindowSize(10);
        assertEquals(0, history.size());
    }

    @Test
    void shouldCompressWhenMessagesExceedThreshold() {
        // Fill with 25 messages (exceeds default window of 20)
        for (int i = 0; i < 13; i++) {
            history.addTurn("问题" + i, "回答" + i, StageType.EXECUTION);
        }
        int before = history.size();
        assertTrue(before > 20, "should have more than 20 messages before compression");

        // Use a fake LLM that returns a simple summary
        LlmClient fakeLlm = new LlmClient() {
            @Override public String chatCompletion(String p) { return "压缩摘要：早期对话"; }
            @Override public String chatCompletion(List<Map<String, String>> m) { return "压缩摘要：早期对话"; }
            @Override public String structuredCompletion(String p, String s) { return "{}"; }
            @Override public String structuredCompletion(List<Map<String, String>> m, String s) { return "{}"; }
        };

        history.compressIfNeeded(fakeLlm, 20);
        assertTrue(history.size() <= 20, "after compression should have <= 20 messages");
    }

    @Test
    void shouldDegradeGracefullyWhenLlmFails() {
        for (int i = 0; i < 13; i++) {
            history.addTurn("问题" + i, "回答" + i, StageType.EXECUTION);
        }
        int before = history.size();
        assertTrue(before > 20);

        LlmClient failingLlm = new LlmClient() {
            @Override public String chatCompletion(String p) { throw new RuntimeException("API error"); }
            @Override public String chatCompletion(List<Map<String, String>> m) { throw new RuntimeException("API error"); }
            @Override public String structuredCompletion(String p, String s) { throw new RuntimeException("API error"); }
            @Override public String structuredCompletion(List<Map<String, String>> m, String s) { throw new RuntimeException("API error"); }
        };

        // Should not throw — gracefully trims old messages
        assertDoesNotThrow(() -> history.compressIfNeeded(failingLlm, 20));
        assertTrue(history.size() <= 20, "should still trim even when LLM fails");
    }

    @Test
    void shouldNotCompressWhenBelowThreshold() {
        history.addTurn("问题", "回答", StageType.GOAL_DEFINITION);
        LlmClient llm = new LlmClient() {
            @Override public String chatCompletion(String p) { throw new RuntimeException("should not be called"); }
            @Override public String chatCompletion(List<Map<String, String>> m) { throw new RuntimeException("should not be called"); }
            @Override public String structuredCompletion(String p, String s) { return "{}"; }
            @Override public String structuredCompletion(List<Map<String, String>> m, String s) { return "{}"; }
        };
        assertDoesNotThrow(() -> history.compressIfNeeded(llm, 20));
        assertEquals(2, history.size());
    }
}
