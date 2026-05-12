package com.yragent.domain.gate.checklist;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

public class EvidenceValidator {

    private static final Set<String> PREFIX_WORDS = Set.of(
            "用户明确提到", "用户明确指出", "开发者表明", "开发者确认",
            "用户确认", "用户表明", "开发者明确", "用户回答",
            "开发者提到", "开发者指出"
    );

    private static final Set<String> VAGUE_WORDS = Set.of(
            "理解正确", "回答充分", "回答正确", "理解到位", "描述清楚",
            "说明完整", "没有问题", "OK", "好的", "可以"
    );

    // 中文词：连续2+个汉字
    private static final Pattern CN = Pattern.compile("[\\u4e00-\\u9fa5]{2,}");
    // 拉丁/技术词：连续2+个字母/数字/点/下划线/斜杠/连字符
    private static final Pattern TECH = Pattern.compile("[a-zA-Z0-9._/-]{2,}");

    public static boolean isEvidenceValid(String evidence, String userInput) {
        if (evidence == null || evidence.isBlank()) return false;
        if (userInput == null || userInput.isBlank()) return false;

        String stripped = stripPrefix(evidence.trim());
        if (stripped.length() < 3) return false;
        if (isAllVague(stripped)) return false;

        for (Pattern p : new Pattern[]{CN, TECH}) {
            var m = p.matcher(stripped);
            while (m.find()) {
                if (userInput.contains(m.group())) return true;
            }
        }
        return false;
    }

    private static String stripPrefix(String evidence) {
        for (String prefix : PREFIX_WORDS) {
            if (evidence.startsWith(prefix)) {
                return evidence.substring(prefix.length());
            }
        }
        return evidence;
    }

    private static boolean isAllVague(String text) {
        String normalized = text.replaceAll("[，,。.！!？?\\s]", "");
        return VAGUE_WORDS.contains(normalized) || normalized.length() < 3;
    }
}
