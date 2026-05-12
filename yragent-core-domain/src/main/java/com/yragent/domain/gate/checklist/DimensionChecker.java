package com.yragent.domain.gate.checklist;

import java.util.Set;

public class DimensionChecker {

    private static final int MIN_ANSWER_LENGTH = 4;

    // 如果用户回答中出现这些词，即使 LLM 标 covered 也强制 partial
    private static final Set<String> FORBIDDEN = Set.of(
            "所有工具", "随便", "无所谓", "都可以", "按你的来",
            "你决定", "你看着办", "我不确定", "不知道"
    );

    public static boolean isDimensionCovered(GateCheckItem item, String userInput) {
        if (item == null || userInput == null || userInput.isBlank()) return false;

        // 回答太短 → 不通过
        if (userInput.trim().length() < MIN_ANSWER_LENGTH) return false;

        // 含禁词 → 不通过
        for (String forbidden : FORBIDDEN) {
            if (userInput.contains(forbidden)) return false;
        }

        // 至少命中一个关键词 → 通过
        for (String keyword : item.keywords()) {
            if (userInput.contains(keyword)) return true;
        }

        return false;
    }
}
