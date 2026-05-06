package com.yragent.domain.memory;

import com.yragent.domain.gate.DeveloperUnderstanding;
import com.yragent.domain.gate.GateReviewAttempt;
import com.yragent.domain.gate.GateSemanticReviewResult;
import com.yragent.domain.gate.GateStatus;
import com.yragent.domain.gate.MergedGateReviewResult;
import com.yragent.domain.gate.PendingDecision;
import com.yragent.domain.gate.PendingDecisionType;
import com.yragent.domain.gate.RuleGateReviewResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateReviewAttemptSerializerTest {

    private final GateReviewAttemptSerializer serializer = new GateReviewAttemptSerializer();

    @Test
    void shouldSerializeAndDeserializeFullAttempt() {
        DeveloperUnderstanding devUnderstanding = new DeveloperUnderstanding(
                "已明确任务目标",
                "风险在于工具授权边界"
        );
        RuleGateReviewResult ruleResult = new RuleGateReviewResult(
                GateStatus.NEEDS_CONFIRMATION,
                "当前阶段等待开发者确认",
                true,
                false,
                List.of(new PendingDecision(PendingDecisionType.CONFIRMATION, "goal.confirm", "确认", "desc", true)),
                List.of("需要确认"),
                "waiting for confirmation"
        );
        GateSemanticReviewResult semanticResult = new GateSemanticReviewResult(
                true,
                false,
                false,
                List.of("工具范围"),
                List.of("误解点"),
                List.of("风险盲区"),
                List.of("建议追问"),
                List.of("反馈项"),
                null
        );
        MergedGateReviewResult mergedResult = new MergedGateReviewResult(
                GateStatus.NEEDS_CLARIFICATION,
                "合并结论",
                List.of(),
                List.of("feedback"),
                true,
                false
        );
        GateReviewAttempt original = new GateReviewAttempt(
                2,
                Instant.now(),
                devUnderstanding,
                ruleResult,
                semanticResult,
                mergedResult,
                GateStatus.NEEDS_CLARIFICATION,
                List.of("备注1")
        );

        String json = serializer.serialize(original);
        GateReviewAttempt restored = serializer.deserialize(json);

        assertEquals(2, restored.getAttemptIndex());
        assertEquals(GateStatus.NEEDS_CLARIFICATION, restored.getFinalStatus());
        assertNotNull(restored.getDeveloperUnderstanding());
        assertEquals("已明确任务目标", restored.getDeveloperUnderstanding().getStageSummary());
        assertNotNull(restored.getRuleResult());
        assertEquals(GateStatus.NEEDS_CONFIRMATION, restored.getRuleResult().getGateStatus());
        assertNotNull(restored.getSemanticResult());
        assertTrue(restored.getSemanticResult().isReviewApplied());
        assertNotNull(restored.getMergedResult());
        assertEquals(1, restored.getAttemptNotes().size());
    }

    @Test
    void shouldHandleMinimalAttempt() {
        RuleGateReviewResult ruleResult = new RuleGateReviewResult(
                GateStatus.BLOCKED,
                "阻断",
                false,
                false,
                List.of(),
                List.of(),
                "reason"
        );
        MergedGateReviewResult mergedResult = new MergedGateReviewResult(
                GateStatus.BLOCKED,
                "阻断",
                List.of(),
                List.of(),
                false,
                false
        );
        GateReviewAttempt original = new GateReviewAttempt(
                1,
                Instant.EPOCH,
                null,
                ruleResult,
                null,
                mergedResult,
                GateStatus.BLOCKED,
                List.of()
        );

        String json = serializer.serialize(original);
        GateReviewAttempt restored = serializer.deserialize(json);

        assertEquals(1, restored.getAttemptIndex());
        assertEquals(GateStatus.BLOCKED, restored.getFinalStatus());
        assertEquals(null, restored.getDeveloperUnderstanding());
        assertEquals(null, restored.getSemanticResult());
    }
}
