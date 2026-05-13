package com.yragent.domain.gate.checklist;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RoundConsistencyChecker {

    private static final double GROWTH_THRESHOLD = 1.3;

    // 同一 item 两轮评分不变 + 用户输入增长 >30% → 标记为 LLM 无响应变更，降级为 partial
    public static List<ItemCoverage> check(
            List<ItemCoverage> prevScores,
            List<ItemCoverage> curScores,
            String prevInput,
            String curInput
    ) {
        if (curScores == null) return List.of();
        if (prevScores == null || prevScores.isEmpty() || prevInput == null || curInput == null) {
            return curScores;
        }

        double growth = (double) curInput.length() / Math.max(prevInput.length(), 1);
        if (growth < GROWTH_THRESHOLD) return curScores;

        Map<String, ItemCoverage> prevMap = prevScores.stream()
                .collect(Collectors.toMap(ItemCoverage::itemId, s -> s));

        List<ItemCoverage> result = new ArrayList<>();
        for (ItemCoverage cur : curScores) {
            ItemCoverage prev = prevMap.get(cur.itemId());
            if (prev != null
                    && prev.status().equals(cur.status())
                    && !ItemCoverage.COVERED.equals(cur.status())) {
                result.add(new ItemCoverage(cur.itemId(), ItemCoverage.PARTIAL,
                        cur.evidence(),
                        "[LLM无响应变更] 上一轮同一维度评分未变但用户已补充内容，请重新批改: " + cur.suggestion()));
            } else {
                result.add(cur);
            }
        }
        return result;
    }
}
