package com.yragent.domain.memory;

import com.yragent.domain.model.LlmClient;
import com.yragent.domain.stage.StageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ConversationHistory {

    private static final Logger log = LoggerFactory.getLogger(ConversationHistory.class);

    private final List<ConversationMessage> messages = new ArrayList<>();
    private String summary;
    private int maxWindowSize = 20;

    public void addTurn(String userMessage, String assistantMessage, StageType stage) {
        if (userMessage != null && !userMessage.isBlank()) {
            messages.add(ConversationMessage.create("user", userMessage, stage));
        }
        if (assistantMessage != null && !assistantMessage.isBlank()) {
            messages.add(ConversationMessage.create("assistant", assistantMessage, stage));
        }
    }

    public List<ConversationMessage> getRecentMessages(int count) {
        int fromIndex = Math.max(0, messages.size() - count);
        return List.copyOf(messages.subList(fromIndex, messages.size()));
    }

    public String getSummary() {
        return summary;
    }

    public void setMaxWindowSize(int size) {
        this.maxWindowSize = size;
    }

    public String renderForPrompt(int recentCount) {
        StringBuilder sb = new StringBuilder();
        if (summary != null && !summary.isBlank()) {
            sb.append("=== 对话摘要 ===\n");
            sb.append(summary).append("\n");
        }
        List<ConversationMessage> recent = getRecentMessages(recentCount);
        if (!recent.isEmpty()) {
            sb.append("=== 最近对话 ===\n");
            for (ConversationMessage msg : recent) {
                String roleLabel = switch (msg.role()) {
                    case "user" -> "用户";
                    case "assistant" -> "助手";
                    default -> msg.role();
                };
                sb.append("[").append(msg.stage()).append("] ")
                        .append(roleLabel).append(": ")
                        .append(truncate(msg.content(), 500)).append("\n");
            }
        }
        return sb.toString();
    }

    public void compressIfNeeded(LlmClient llmClient, int threshold) {
        if (messages.size() <= threshold) return;

        int compressCount = messages.size() - threshold;
        List<ConversationMessage> toCompress = new ArrayList<>();
        for (int i = 0; i < compressCount && i < messages.size(); i++) {
            ConversationMessage msg = messages.get(i);
            if (!"system".equals(msg.role())) {
                toCompress.add(msg);
            }
        }

        if (toCompress.isEmpty()) return;

        try {
            String prompt = buildSummaryPrompt(toCompress);
            String newSummary = llmClient.chatCompletion(prompt);
            this.summary = (this.summary != null ? this.summary + "\n" : "") + newSummary;
        } catch (Exception e) {
            log.warn("LLM 摘要压缩失败，降级为直接丢弃旧消息", e);
        }

        // Remove compressed messages
        for (ConversationMessage msg : toCompress) {
            messages.remove(msg);
        }
    }

    private String buildSummaryPrompt(List<ConversationMessage> toCompress) {
        StringBuilder sb = new StringBuilder();
        sb.append("请将以下对话历史压缩为简短摘要（中文，200字以内），保留关键决策和重要信息：\n\n");
        for (ConversationMessage msg : toCompress) {
            String roleLabel = switch (msg.role()) {
                case "user" -> "用户";
                case "assistant" -> "助手";
                default -> msg.role();
            };
            sb.append("[").append(msg.stage()).append("] ")
                    .append(roleLabel).append(": ")
                    .append(truncate(msg.content(), 300)).append("\n");
        }
        return sb.toString();
    }

    private String truncate(String content, int maxLen) {
        if (content == null) return "";
        return content.length() > maxLen ? content.substring(0, maxLen) + "..." : content;
    }

    public int size() {
        return messages.size();
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }
}
