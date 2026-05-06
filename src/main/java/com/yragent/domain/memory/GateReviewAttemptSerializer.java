package com.yragent.domain.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yragent.domain.gate.DeveloperUnderstanding;
import com.yragent.domain.gate.GateReviewAttempt;
import com.yragent.domain.gate.GateSemanticReviewResult;
import com.yragent.domain.gate.GateStatus;
import com.yragent.domain.gate.MergedGateReviewResult;
import com.yragent.domain.gate.PendingDecision;
import com.yragent.domain.gate.PendingDecisionType;
import com.yragent.domain.gate.RuleGateReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// 门禁尝试记录的 JSON 序列化器。
// 因为领域对象是不可变的且没有 Jackson 注解，这里手动构建和解析 JSON。
@Component
public class GateReviewAttemptSerializer {

    private static final Logger log = LoggerFactory.getLogger(GateReviewAttemptSerializer.class);

    private final ObjectMapper objectMapper;

    public GateReviewAttemptSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String serialize(GateReviewAttempt attempt) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("attemptIndex", attempt.getAttemptIndex());
            root.put("timestamp", attempt.getTimestamp().toString());
            root.put("finalStatus", attempt.getFinalStatus().name());

            // 开发者理解
            if (attempt.getDeveloperUnderstanding() != null) {
                var devNode = objectMapper.createObjectNode();
                devNode.put("stageSummary", attempt.getDeveloperUnderstanding().getStageSummary());
                devNode.put("riskSummary", attempt.getDeveloperUnderstanding().getRiskSummary());
                root.set("developerUnderstanding", devNode);
            }

            // 规则层结果
            if (attempt.getRuleResult() != null) {
                var ruleNode = objectMapper.createObjectNode();
                ruleNode.put("gateStatus", attempt.getRuleResult().getGateStatus().name());
                ruleNode.put("stageSummary", attempt.getRuleResult().getStageSummary());
                ruleNode.put("materialsReady", attempt.getRuleResult().isMaterialsReady());
                ruleNode.put("requiredConfirmationsCompleted", attempt.getRuleResult().isRequiredConfirmationsCompleted());
                ruleNode.put("blockedReason", attempt.getRuleResult().getBlockedReason());
                ruleNode.set("pendingDecisions", objectMapper.valueToTree(attempt.getRuleResult().getPendingDecisions()));
                ruleNode.set("feedbackItems", objectMapper.valueToTree(attempt.getRuleResult().getFeedbackItems()));
                root.set("ruleResult", ruleNode);
            }

            // 语义层结果
            if (attempt.getSemanticResult() != null) {
                var semanticNode = objectMapper.createObjectNode();
                semanticNode.put("reviewApplied", attempt.getSemanticResult().isReviewApplied());
                semanticNode.put("fallbackToRuleOnly", attempt.getSemanticResult().isFallbackToRuleOnly());
                semanticNode.put("coveragePassed", attempt.getSemanticResult().isCoveragePassed());
                semanticNode.set("missingTopics", objectMapper.valueToTree(attempt.getSemanticResult().getMissingTopics()));
                semanticNode.set("misunderstandings", objectMapper.valueToTree(attempt.getSemanticResult().getMisunderstandings()));
                semanticNode.set("riskBlindSpots", objectMapper.valueToTree(attempt.getSemanticResult().getRiskBlindSpots()));
                semanticNode.set("suggestedQuestions", objectMapper.valueToTree(attempt.getSemanticResult().getSuggestedQuestions()));
                semanticNode.set("feedbackItems", objectMapper.valueToTree(attempt.getSemanticResult().getFeedbackItems()));
                semanticNode.put("fallbackReason", attempt.getSemanticResult().getFallbackReason());
                root.set("semanticResult", semanticNode);
            }

            // 合并裁决结果
            if (attempt.getMergedResult() != null) {
                var mergedNode = objectMapper.createObjectNode();
                mergedNode.put("gateStatus", attempt.getMergedResult().getGateStatus().name());
                mergedNode.put("stageSummary", attempt.getMergedResult().getStageSummary());
                mergedNode.put("llmReviewApplied", attempt.getMergedResult().isLlmReviewApplied());
                mergedNode.put("fallbackToRuleOnly", attempt.getMergedResult().isFallbackToRuleOnly());
                mergedNode.set("pendingDecisions", objectMapper.valueToTree(attempt.getMergedResult().getPendingDecisions()));
                mergedNode.set("feedbackItems", objectMapper.valueToTree(attempt.getMergedResult().getFeedbackItems()));
                root.set("mergedResult", mergedNode);
            }

            root.set("attemptNotes", objectMapper.valueToTree(attempt.getAttemptNotes()));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to serialize GateReviewAttempt", e);
            throw new IllegalStateException("序列化门禁尝试记录失败", e);
        }
    }

    public GateReviewAttempt deserialize(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            int attemptIndex = root.path("attemptIndex").asInt(0);
            Instant timestamp = parseInstant(root.path("timestamp").asText(null));

            DeveloperUnderstanding devUnderstanding = null;
            if (root.has("developerUnderstanding") && !root.get("developerUnderstanding").isNull()) {
                JsonNode devNode = root.get("developerUnderstanding");
                devUnderstanding = new DeveloperUnderstanding(
                        devNode.path("stageSummary").asText(null),
                        devNode.path("riskSummary").asText(null)
                );
            }

            RuleGateReviewResult ruleResult = null;
            if (root.has("ruleResult") && !root.get("ruleResult").isNull()) {
                JsonNode ruleNode = root.get("ruleResult");
                ruleResult = new RuleGateReviewResult(
                        GateStatus.valueOf(ruleNode.path("gateStatus").asText("BLOCKED")),
                        ruleNode.path("stageSummary").asText(null),
                        ruleNode.path("materialsReady").asBoolean(false),
                        ruleNode.path("requiredConfirmationsCompleted").asBoolean(false),
                        parsePendingDecisions(ruleNode.path("pendingDecisions")),
                        parseStringList(ruleNode.path("feedbackItems")),
                        ruleNode.path("blockedReason").asText(null)
                );
            }

            GateSemanticReviewResult semanticResult = null;
            if (root.has("semanticResult") && !root.get("semanticResult").isNull()) {
                JsonNode semanticNode = root.get("semanticResult");
                boolean reviewApplied = semanticNode.path("reviewApplied").asBoolean(false);
                boolean fallback = semanticNode.path("fallbackToRuleOnly").asBoolean(false);
                boolean coveragePassed = semanticNode.path("coveragePassed").asBoolean(true);
                if (reviewApplied || fallback) {
                    semanticResult = new GateSemanticReviewResult(
                            reviewApplied,
                            fallback,
                            coveragePassed,
                            parseStringList(semanticNode.path("missingTopics")),
                            parseStringList(semanticNode.path("misunderstandings")),
                            parseStringList(semanticNode.path("riskBlindSpots")),
                            parseStringList(semanticNode.path("suggestedQuestions")),
                            parseStringList(semanticNode.path("feedbackItems")),
                            semanticNode.path("fallbackReason").asText(null)
                    );
                }
            }

            MergedGateReviewResult mergedResult = null;
            if (root.has("mergedResult") && !root.get("mergedResult").isNull()) {
                JsonNode mergedNode = root.get("mergedResult");
                mergedResult = new MergedGateReviewResult(
                        GateStatus.valueOf(mergedNode.path("gateStatus").asText("BLOCKED")),
                        mergedNode.path("stageSummary").asText(null),
                        parsePendingDecisions(mergedNode.path("pendingDecisions")),
                        parseStringList(mergedNode.path("feedbackItems")),
                        mergedNode.path("llmReviewApplied").asBoolean(false),
                        mergedNode.path("fallbackToRuleOnly").asBoolean(false)
                );
            }

            GateStatus finalStatus = GateStatus.valueOf(root.path("finalStatus").asText("BLOCKED"));
            List<String> attemptNotes = parseStringList(root.path("attemptNotes"));

            return new GateReviewAttempt(
                    attemptIndex,
                    timestamp,
                    devUnderstanding,
                    ruleResult,
                    semanticResult,
                    mergedResult,
                    finalStatus,
                    attemptNotes
            );
        } catch (Exception e) {
            log.error("Failed to deserialize GateReviewAttempt", e);
            throw new IllegalStateException("反序列化门禁尝试记录失败", e);
        }
    }

    private List<PendingDecision> parsePendingDecisions(JsonNode arrayNode) {
        List<PendingDecision> decisions = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                decisions.add(new PendingDecision(
                        PendingDecisionType.valueOf(item.path("type").asText("CONFIRMATION")),
                        item.path("code").asText(""),
                        item.path("title").asText(""),
                        item.path("description").asText(""),
                        item.path("required").asBoolean(true)
                ));
            }
        }
        return decisions;
    }

    private List<String> parseStringList(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                if (!item.isNull()) {
                    values.add(item.asText(""));
                }
            }
        }
        return values;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }
}
