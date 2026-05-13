package com.yragent.domain.gate.step;

import com.yragent.domain.gate.checklist.GateCheckItem;
import com.yragent.domain.gate.checklist.ItemCoverage;
import com.yragent.domain.model.LlmClient;
import com.yragent.domain.stage.StageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CoverageReviewStep {

    private static final Logger log = LoggerFactory.getLogger(CoverageReviewStep.class);
    private final LlmClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public CoverageReviewStep(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public List<ItemCoverage> review(List<GateCheckItem> items, String userInput,
                                      StageType stage, String goalSummary, String planSummary) {
        if (items == null || items.isEmpty()) return List.of();

        try {
            String prompt = buildPrompt(items, userInput, stage, goalSummary, planSummary);
            String response = llmClient.structuredCompletion(prompt, SCHEMA);
            return parseResponse(response, items);
        } catch (Exception e) {
            log.warn("CoverageReview LLM call failed, falling back: {}", e.getMessage());
            return fallbackReview(items, userInput);
        }
    }

    private String buildPrompt(List<GateCheckItem> items, String userInput,
                                StageType stage, String goalSummary, String planSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是门禁批改者。对照以下 checklist 逐项评估用户回答的覆盖度。\n\n");
        sb.append("阶段: ").append(stage).append("\n");
        sb.append("目标: ").append(goalSummary).append("\n");
        sb.append("计划: ").append(planSummary).append("\n\n");
        sb.append("检查清单:\n");
        for (GateCheckItem item : items) {
            sb.append("- [").append(item.id()).append("] ").append(item.dimension())
              .append(": ").append(item.question()).append("\n");
        }
        sb.append("\n用户回答:\n").append(userInput).append("\n\n");
        sb.append("对每一项输出JSON数组: [{\"itemId\",\"status\":\"covered|partial|missing\",\"evidence\":\"用户的具体表述(必须引用原文)\",\"suggestion\":\"未覆盖时的追问\"}]\n");
        sb.append("规则: 空泛回答(如\"我理解了\")→partial或missing; 用户明确提及维度具体内容→covered; 禁止因表述不专业扣分。");
        return sb.toString();
    }

    private List<ItemCoverage> parseResponse(String json, List<GateCheckItem> items) {
        try {
            String extracted = extractJsonArray(json);
            JsonNode root = mapper.readTree(extracted);
            List<ItemCoverage> result = new ArrayList<>();
            for (JsonNode node : root) {
                result.add(new ItemCoverage(
                        node.path("itemId").asText(""),
                        node.path("status").asText("partial"),
                        node.path("evidence").asText(""),
                        node.path("suggestion").asText("")
                ));
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse LLM response: {}", e.getMessage());
            return fallbackReview(items, "");
        }
    }

    private List<ItemCoverage> fallbackReview(List<GateCheckItem> items, String userInput) {
        List<ItemCoverage> result = new ArrayList<>();
        for (GateCheckItem item : items) {
            boolean hit = item.keywords().stream().anyMatch(userInput::contains);
            result.add(new ItemCoverage(item.id(),
                    hit ? ItemCoverage.PARTIAL : ItemCoverage.MISSING,
                    hit ? "fallback: 关键词匹配" : "fallback: 未命中关键词",
                    hit ? "需要 LLM 进一步确认" : "LLM API error - 请重新提交"));
        }
        return result;
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') { depth--; if (depth == 0) return text.substring(start, i + 1); }
        }
        return text;
    }

    private static final String SCHEMA = """
            {"type":"array","items":{"type":"object","properties":{"itemId":{"type":"string"},"status":{"type":"string","enum":["covered","partial","missing"]},"evidence":{"type":"string"},"suggestion":{"type":"string"}},"required":["itemId","status","evidence","suggestion"]}}""";
}
