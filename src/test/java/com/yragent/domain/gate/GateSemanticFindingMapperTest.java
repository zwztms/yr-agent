package com.yragent.domain.gate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateSemanticFindingMapperTest {

    private final GateSemanticFindingMapper mapper = new GateSemanticFindingMapper();

    @Test
    void shouldMapAllFieldsFromLlmResponse() {
        LlmGateReviewResponse response = new LlmGateReviewResponse();
        response.setCoveragePassed(false);
        response.setCoveredTopics(List.of("任务目标", "风险授权"));
        response.setMissingTopics(List.of("工具范围"));
        response.setMisunderstandings(List.of("将门禁理解为自动审批"));
        response.setRiskBlindSpots(List.of("未说明人工接管条件"));
        response.setSuggestedQuestions(List.of("请解释为什么停在门禁阶段"));
        response.setRawSummary("评审发现关键缺口");

        GateSemanticReviewResult result = mapper.map(response);

        assertTrue(result.isReviewApplied());
        assertFalse(result.isFallbackToRuleOnly());
        assertFalse(result.isCoveragePassed());
        assertTrue(result.requiresClarification());
        assertEquals(1, result.getMissingTopics().size());
        assertEquals(1, result.getMisunderstandings().size());
        assertEquals(1, result.getRiskBlindSpots().size());
        assertEquals(1, result.getSuggestedQuestions().size());
        assertEquals(4, result.getFeedbackItems().size());
    }

    @Test
    void shouldHandleEmptyResponse() {
        LlmGateReviewResponse response = new LlmGateReviewResponse();
        response.setCoveragePassed(true);
        response.setCoveredTopics(List.of());
        response.setMissingTopics(List.of());
        response.setMisunderstandings(List.of());
        response.setRiskBlindSpots(List.of());
        response.setSuggestedQuestions(List.of());
        response.setRawSummary("无发现问题");

        GateSemanticReviewResult result = mapper.map(response);

        assertTrue(result.isReviewApplied());
        assertTrue(result.isCoveragePassed());
        assertFalse(result.requiresClarification());
        assertTrue(result.getFeedbackItems().isEmpty());
    }

    @Test
    void shouldHandleNullListsInResponse() {
        LlmGateReviewResponse response = new LlmGateReviewResponse();
        response.setCoveragePassed(true);
        response.setCoveredTopics(null);
        response.setMissingTopics(null);
        response.setMisunderstandings(null);
        response.setRiskBlindSpots(null);
        response.setSuggestedQuestions(null);
        response.setRawSummary(null);

        GateSemanticReviewResult result = mapper.map(response);

        assertTrue(result.isReviewApplied());
        assertTrue(result.isCoveragePassed());
        assertFalse(result.requiresClarification());
        assertTrue(result.getFeedbackItems().isEmpty());
    }
}
