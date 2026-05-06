package com.yragent.domain.gate.step;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yragent.domain.gate.DeveloperUnderstanding;
import com.yragent.domain.gate.GateSemanticFindingMapper;
import com.yragent.domain.gate.GateSemanticReviewResult;
import com.yragent.domain.gate.LlmGateReviewRequest;
import com.yragent.domain.gate.LlmGateReviewResponse;
import com.yragent.domain.gate.PendingDecision;
import com.yragent.domain.gate.PendingDecisionType;
import com.yragent.domain.gate.RuleGateReviewResult;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.stage.TaskExecutionContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GateSemanticReviewStep {

    private final LlmClient llmClient;
    private final GateSemanticFindingMapper findingMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GateSemanticReviewStep(LlmClient llmClient, GateSemanticFindingMapper findingMapper) {
        this.llmClient = llmClient;
        this.findingMapper = findingMapper;
    }

    public GateSemanticReviewResult review(TaskExecutionContext context,
                                           RuleGateReviewResult ruleResult) {
        if (!shouldReview(ruleResult)) {
            return GateSemanticReviewResult.notApplied();
        }

        try {
            LlmGateReviewRequest request = buildRequest(context, ruleResult);
            String response = llmClient.structuredCompletion(buildPrompt(request), buildSchema());
            LlmGateReviewResponse parsedResponse = parseResponse(response);
            return toSemanticResult(parsedResponse);
        } catch (UnsupportedOperationException exception) {
            return GateSemanticReviewResult.fallback("structured LLM gate review is not implemented yet");
        } catch (Exception exception) {
            return GateSemanticReviewResult.fallback("semantic gate review fallback: " + exception.getClass().getSimpleName());
        }
    }

    private boolean shouldReview(RuleGateReviewResult ruleResult) {
        return ruleResult.isMaterialsReady();
    }

    private LlmGateReviewRequest buildRequest(TaskExecutionContext context,
                                              RuleGateReviewResult ruleResult) {
        DeveloperUnderstanding developerUnderstanding = context.getDeveloperUnderstanding();
        List<String> requiredTopics = new ArrayList<>();
        requiredTopics.add("任务目标");
        requiredTopics.add("当前处于门禁阶段的原因");
        requiredTopics.add("风险与授权口径");
        boolean approachPlanEmpty = context.getApproachPlan() == null
                || context.getApproachPlan().equals(com.yragent.domain.planning.ApproachPlan.empty());
        if (!approachPlanEmpty) {
            requiredTopics.add("规划阶段筛出的工具范围");
        }

        List<String> currentPendingConfirmations = ruleResult.getPendingDecisions().stream()
                .filter(decision -> decision.getType() == PendingDecisionType.CONFIRMATION)
                .map(PendingDecision::getCode)
                .toList();

        return new LlmGateReviewRequest(
                context.getTaskId(),
                context.getCurrentStage(),
                ruleResult.getStageSummary(),
                List.copyOf(context.getStageNotes()),
                developerUnderstanding == null ? null : developerUnderstanding.getStageSummary(),
                developerUnderstanding == null ? null : developerUnderstanding.getRiskSummary(),
                requiredTopics,
                currentPendingConfirmations,
                "检查开发者复述是否覆盖关键边界、误解点和风险盲区"
        );
    }

    private String buildPrompt(LlmGateReviewRequest request) throws JsonProcessingException {
        return """
                你是门禁语义评审器。请仅根据输入内容，检查开发者复述是否覆盖关键点，并输出 JSON。
                要求：
                1. 不要输出解释性文本，只输出 JSON。
                2. 如果没有发现缺口，相关数组输出空数组。
                3. 不要编造输入中不存在的实现。
                输入：
                %s
                """.formatted(objectMapper.writeValueAsString(request));
    }

    private String buildSchema() {
        return """
                {
                  "type":"object",
                  "properties":{
                    "coveragePassed":{"type":"boolean"},
                    "coveredTopics":{"type":"array","items":{"type":"string"}},
                    "missingTopics":{"type":"array","items":{"type":"string"}},
                    "misunderstandings":{"type":"array","items":{"type":"string"}},
                    "riskBlindSpots":{"type":"array","items":{"type":"string"}},
                    "suggestedQuestions":{"type":"array","items":{"type":"string"}},
                    "rawSummary":{"type":"string"}
                  },
                  "required":["coveragePassed","coveredTopics","missingTopics","misunderstandings","riskBlindSpots","suggestedQuestions","rawSummary"]
                }
                """;
    }

    private LlmGateReviewResponse parseResponse(String response) throws JsonProcessingException {
        String jsonPayload = extractJsonObject(response);
        JsonNode root = objectMapper.readTree(jsonPayload);

        LlmGateReviewResponse parsed = new LlmGateReviewResponse();
        parsed.setCoveragePassed(root.path("coveragePassed").asBoolean(true));
        parsed.setCoveredTopics(readStringList(root, "coveredTopics"));
        parsed.setMissingTopics(readStringList(root, "missingTopics"));
        parsed.setMisunderstandings(readStringList(root, "misunderstandings"));
        parsed.setRiskBlindSpots(readStringList(root, "riskBlindSpots"));
        parsed.setSuggestedQuestions(readStringList(root, "suggestedQuestions"));
        parsed.setRawSummary(root.path("rawSummary").asText(""));
        return parsed;
    }

    private GateSemanticReviewResult toSemanticResult(LlmGateReviewResponse response) {
        return findingMapper.map(response);
    }

    private List<String> readStringList(JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item != null && !item.isNull()) {
                String value = item.asText("").trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        });
        return values;
    }

    private String extractJsonObject(String rawResponse) {
        if (rawResponse == null) {
            throw new IllegalArgumentException("semantic review response is null");
        }

        String trimmed = rawResponse.trim();
        int start = trimmed.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("semantic review response does not contain JSON object");
        }

        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int index = start; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaping = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return trimmed.substring(start, index + 1);
                }
            }
        }

        throw new IllegalArgumentException("semantic review response contains incomplete JSON object");
    }
}
