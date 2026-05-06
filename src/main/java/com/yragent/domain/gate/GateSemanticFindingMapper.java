package com.yragent.domain.gate;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 语义发现映射层：LLM 负责"发现"，映射层负责"转成缺口与反馈"。
// 隔开 LLM 输出格式变化对领域对象的影响。
@Component
public class GateSemanticFindingMapper {

    // 把 LLM 评审响应转成结构化语义评审结果，提取覆盖情况、缺失主题、误解、风险盲区和建议追问。
    public GateSemanticReviewResult map(LlmGateReviewResponse response) {
        List<String> missingTopics = defaultList(response.getMissingTopics());
        List<String> misunderstandings = defaultList(response.getMisunderstandings());
        List<String> riskBlindSpots = defaultList(response.getRiskBlindSpots());
        List<String> suggestedQuestions = defaultList(response.getSuggestedQuestions());
        List<String> feedbackItems = new ArrayList<>();
        missingTopics.forEach(item -> feedbackItems.add("语义评审发现缺失主题: " + item));
        misunderstandings.forEach(item -> feedbackItems.add("语义评审发现疑似误解: " + item));
        riskBlindSpots.forEach(item -> feedbackItems.add("语义评审发现风险盲区: " + item));
        suggestedQuestions.forEach(item -> feedbackItems.add("建议继续追问: " + item));

        return new GateSemanticReviewResult(
                true,
                false,
                response.isCoveragePassed(),
                missingTopics,
                misunderstandings,
                riskBlindSpots,
                suggestedQuestions,
                feedbackItems,
                null
        );
    }

    private List<String> defaultList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
