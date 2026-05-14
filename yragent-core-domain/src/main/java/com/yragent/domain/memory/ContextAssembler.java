package com.yragent.domain.memory;

import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(ContextAssembler.class);

    private final MemoryService memoryService;

    public ContextAssembler(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    public String renderContext(StageType stageType, TaskExecutionContext context, int maxMemories) {
        StringBuilder sb = new StringBuilder();
        sb.append("【上下文记忆】\n");

        int count = 0;

        // 1. Preference zone
        UserPreference prefs = context.getUserPreference();
        ProjectPolicy policy = context.getProjectPolicy();
        if (prefs != null || policy != null) {
            sb.append(renderPreferenceBlock(prefs, policy));
            count++;
        }

        // 2. Memory fragments for this stage
        List<MemoryFragment> fragments = memoryService.loadForStage(stageType, context);
        if (!fragments.isEmpty()) {
            sb.append(renderMemoryBlock(fragments, maxMemories));
            count += Math.min(fragments.size(), maxMemories);
        }

        // 3. Conversation history from current task
        ConversationHistory history = context.getConversationHistory();
        if (history != null && !history.isEmpty()) {
            String rendered = history.renderForPrompt(5);
            if (!rendered.isBlank()) {
                sb.append(rendered);
                count++;
            }
        }

        // 4. Round history for multi-round tasks
        if (context.getCurrentRound() > 0 && !context.getRoundHistory().isEmpty()) {
            sb.append(renderRoundSummary(context));
            count++;
        }

        sb.append("\n--- 以上是上下文，以下是当前任务 ---\n");
        log.debug("ContextAssembler: stage={}, {} context blocks assembled", stageType, count);
        return sb.toString();
    }

    private String renderPreferenceBlock(UserPreference prefs, ProjectPolicy policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("[偏好] ");
        if (prefs != null) {
            sb.append("riskTolerance=").append(prefs.getRiskTolerance())
                    .append(", confirmation=").append(prefs.getConfirmationMode())
                    .append(", maxTools=").append(prefs.getMaxToolsPerTask());
        }
        if (policy != null) {
            sb.append(" | projectType=").append(policy.getProjectType())
                    .append(", allowNetwork=").append(policy.isAllowNetworkAccess());
            if (!policy.getDirectoryExclusions().isEmpty()) {
                sb.append(", excludeDirs=").append(policy.getDirectoryExclusions());
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private String renderMemoryBlock(List<MemoryFragment> fragments, int max) {
        StringBuilder sb = new StringBuilder();
        sb.append("[相关记忆]\n");
        fragments.stream()
                .sorted((a, b) -> Double.compare(b.getPriority(), a.getPriority()))
                .limit(max)
                .forEach(f -> {
                    MemoryZone zone = f.getZone();
                    sb.append("[").append(zone != null ? zone.name() : "UNKNOWN").append("] ");
                    sb.append(f.getTitle());
                    String content = f.getContent();
                    if (content != null && !content.isBlank()) {
                        sb.append(": ").append(truncate(content, 200));
                    }
                    sb.append("\n");
                });
        return sb.toString();
    }

    private String renderRoundSummary(TaskExecutionContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("[多轮摘要] 当前第").append(context.getCurrentRound()).append("轮");
        int total = context.getRoundHistory().size();
        if (total > 0) {
            var last = context.getRoundHistory().get(total - 1);
            sb.append("，上一轮: ").append(truncate(last.getReviewSummary(), 150));
        }
        sb.append("\n");
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
