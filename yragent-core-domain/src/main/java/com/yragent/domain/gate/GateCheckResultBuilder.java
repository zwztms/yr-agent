package com.yragent.domain.gate;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GateCheckResultBuilder {

    public GateCheckResult build(MergedGateReviewResult mergedResult) {
        GateReviewNote gateReviewNote = new GateReviewNote(
                mergedResult.getGateStatus(),
                buildSummary(mergedResult.getGateStatus()),
                buildFeedbackItems(mergedResult),
                buildEvidence(mergedResult)
        );
        return new GateCheckResult(
                mergedResult.getGateStatus(),
                buildResultSummary(mergedResult.getGateStatus()),
                mergedResult.getStageSummary(),
                gateReviewNote,
                mergedResult.getPendingDecisions()
        );
    }

    public GateCheckResult buildFromRule(RuleGateReviewResult ruleResult) {
        GateReviewNote gateReviewNote = new GateReviewNote(
                ruleResult.getGateStatus(),
                "当前阶段仍存在认知缺口或确认项未闭环。",
                ruleResult.getFeedbackItems(),
                buildRuleOnlyEvidence(ruleResult)
        );
        return new GateCheckResult(
                ruleResult.getGateStatus(),
                buildResultSummary(ruleResult.getGateStatus()),
                ruleResult.getStageSummary(),
                gateReviewNote,
                ruleResult.getPendingDecisions()
        );
    }

    private String buildSummary(GateStatus gateStatus) {
        return switch (gateStatus) {
            case PASS -> "开发者已完成阶段复述、风险判断和关键确认。";
            case NEEDS_CONFIRMATION -> "当前阶段理解已提交，但确认项仍未闭环。";
            case NEEDS_CLARIFICATION -> "当前阶段仍需进一步澄清开发者理解。";
            case BLOCKED -> "当前阶段仍存在认知缺口或确认项未闭环。";
        };
    }

    private String buildResultSummary(GateStatus gateStatus) {
        return switch (gateStatus) {
            case PASS -> "gate passed: developer confirmations completed";
            case NEEDS_CONFIRMATION -> "gate blocked: waiting developer confirmation";
            case NEEDS_CLARIFICATION -> "gate blocked: waiting developer clarification";
            case BLOCKED -> "gate blocked: developer input is incomplete";
        };
    }

    private List<String> buildFeedbackItems(MergedGateReviewResult mergedResult) {
        List<String> feedbackItems = new ArrayList<>(mergedResult.getFeedbackItems());
        if (mergedResult.getGateStatus().isPassed()) {
            feedbackItems.add("当前门禁已通过，可以继续进入执行阶段。");
        }
        return feedbackItems;
    }

    // 生成本轮门禁的裁决依据，按"规则层 → 语义层 → 合并裁决"顺序组织，让开发者能看清楚每一步的判断理由。
    private List<String> buildEvidence(MergedGateReviewResult mergedResult) {
        List<String> evidence = new ArrayList<>();

        // 规则层依据
        if (!mergedResult.getFeedbackItems().isEmpty()) {
            evidence.add("规则层：材料齐全并通过底线检查");
        }
        // 语义层依据
        if (mergedResult.isLlmReviewApplied()) {
            evidence.add("语义层：已执行 LLM 语义评审");
        } else if (mergedResult.isFallbackToRuleOnly()) {
            evidence.add("语义层：LLM 评审不可用，本轮降级为纯规则门禁");
        } else {
            evidence.add("语义层：未触发语义评审（规则层已阻断）");
        }
        // 合并裁决
        evidence.add(switch (mergedResult.getGateStatus()) {
            case PASS -> "合并裁决：PASS（放行）——规则通过且语义未发现关键缺口";
            case NEEDS_CLARIFICATION -> "合并裁决：NEEDS_CLARIFICATION（需要澄清）——语义层发现缺口或误解";
            case NEEDS_CONFIRMATION -> "合并裁决：NEEDS_CONFIRMATION（待确认）——确认项未闭环";
            case BLOCKED -> "合并裁决：BLOCKED（阻断）——开发者输入未完成";
        });

        return evidence;
    }

    private List<String> buildRuleOnlyEvidence(RuleGateReviewResult ruleResult) {
        List<String> evidence = new ArrayList<>();
        evidence.add("规则层阻断：开发者输入不完整，跳过语义评审");
        evidence.add("合并裁决：BLOCKED（阻断）——" + ruleResult.getBlockedReason());
        return evidence;
    }
}
