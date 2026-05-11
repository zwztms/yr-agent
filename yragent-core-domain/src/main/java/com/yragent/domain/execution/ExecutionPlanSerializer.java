package com.yragent.domain.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExecutionPlanSerializer {

    private static final Logger log = LoggerFactory.getLogger(ExecutionPlanSerializer.class);

    private final ObjectMapper objectMapper;

    public ExecutionPlanSerializer() {
        this.objectMapper = new ObjectMapper();
    }

    public String serialize(ExecutionPlan plan) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("rationale", plan.getRationale());
            var stepsArray = objectMapper.createArrayNode();
            for (ExecutionPlan.ExecutionStep step : plan.getSteps()) {
                var stepNode = objectMapper.createObjectNode();
                stepNode.put("index", step.index());
                stepNode.put("tool", step.tool());
                stepNode.set("params", objectMapper.valueToTree(step.params()));
                stepNode.put("reason", step.reason());
                stepsArray.add(stepNode);
            }
            root.set("steps", stepsArray);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to serialize ExecutionPlan", e);
            throw new IllegalStateException("序列化执行计划失败", e);
        }
    }

    public ExecutionPlan deserialize(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String rationale = root.path("rationale").asText("");
            List<ExecutionPlan.ExecutionStep> steps = new ArrayList<>();
            JsonNode stepsNode = root.path("steps");
            if (stepsNode.isArray()) {
                for (JsonNode stepNode : stepsNode) {
                    int index = stepNode.path("index").asInt(0);
                    String tool = stepNode.path("tool").asText("");
                    String reason = stepNode.path("reason").asText("");
                    Map<String, String> params = parseParams(stepNode.path("params"));
                    if (!tool.isBlank()) {
                        steps.add(new ExecutionPlan.ExecutionStep(index, tool, params, reason));
                    }
                }
            }
            return new ExecutionPlan(steps, rationale);
        } catch (Exception e) {
            log.error("Failed to deserialize ExecutionPlan", e);
            throw new IllegalStateException("反序列化执行计划失败: " + json, e);
        }
    }

    // 尝试从 LLM 返回的文本中提取 JSON 执行计划。
    public ExecutionPlan deserializeFromLlmOutput(String llmOutput) {
        String json = extractJson(llmOutput);
        return deserialize(json);
    }

    private Map<String, String> parseParams(JsonNode paramsNode) {
        Map<String, String> params = new HashMap<>();
        if (paramsNode != null && paramsNode.isObject()) {
            var fields = paramsNode.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String value = field.getValue().isNull() ? "" : field.getValue().asText("");
                params.put(field.getKey(), value);
            }
        }
        return params;
    }

    // 从 LLM 输出中提取 JSON 块，兼容 markdown 代码块包裹。
    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        String trimmed = raw.trim();
        int jsonStart = trimmed.indexOf("```json");
        if (jsonStart >= 0) {
            int contentStart = trimmed.indexOf('\n', jsonStart);
            int jsonEnd = trimmed.indexOf("```", contentStart > 0 ? contentStart : jsonStart + 7);
            if (contentStart > 0 && jsonEnd > contentStart) {
                return trimmed.substring(contentStart, jsonEnd).trim();
            }
        }
        // 尝试找裸 JSON 对象
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1).trim();
        }
        return trimmed;
    }
}
