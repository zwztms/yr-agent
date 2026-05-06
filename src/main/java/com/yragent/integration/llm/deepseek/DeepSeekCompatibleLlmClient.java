package com.yragent.integration.llm.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yragent.domain.model.LlmClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "yragent.llm", name = "provider", havingValue = "deepseek-compatible", matchIfMissing = true)
public class DeepSeekCompatibleLlmClient implements LlmClient {

    private static final String DEFAULT_SYSTEM_PROMPT = "你是 yragent 的模型调用实现，请严格遵守用户输入和输出约束。";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;
    private final String modelName;
    private final String configuredApiKey;
    private final String completionsPath;

    public DeepSeekCompatibleLlmClient(@Value("${yragent.llm.base-url:https://api.deepseek.com}") String baseUrl,
                                       @Value("${yragent.llm.executor-model:deepseek-chat}") String modelName,
                                       @Value("${yragent.llm.api-key:}") String configuredApiKey,
                                       @Value("${yragent.llm.completions-path:/v1/chat/completions}") String completionsPath) {
        this.restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(baseUrl))
                .build();
        this.modelName = modelName;
        this.configuredApiKey = configuredApiKey;
        this.completionsPath = ensureLeadingSlash(completionsPath);
    }

    @Override
    public String chatCompletion(String prompt) {
        return requestCompletion(List.of(
                message("system", DEFAULT_SYSTEM_PROMPT),
                message("user", prompt)
        ), false);
    }

    @Override
    public String structuredCompletion(String prompt, String schema) {
        return requestCompletion(List.of(
                message("system", buildStructuredSystemPrompt(schema)),
                message("user", prompt)
        ), true);
    }

    private String requestCompletion(List<Map<String, Object>> messages, boolean jsonOutput) {
        String apiKey = resolveApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new UnsupportedOperationException("DeepSeek API key is not configured");
        }

        Map<String, Object> body = Map.of(
                "model", modelName,
                "messages", messages,
                "stream", false,
                "temperature", jsonOutput ? 0.1 : 0.7,
                "response_format", Map.of("type", jsonOutput ? "json_object" : "text")
        );

        String rawResponse = restClient.post()
                .uri(completionsPath)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(String.class);

        return extractAssistantContent(rawResponse);
    }

    private String extractAssistantContent(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                return contentNode.asText("");
            }
            throw new IllegalStateException("DeepSeek response does not contain choices[0].message.content");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse DeepSeek completion response", exception);
        }
    }

    private String resolveApiKey() {
        if (StringUtils.hasText(configuredApiKey)) {
            return configuredApiKey;
        }
        String deepSeekApiKey = System.getenv("DEEPSEEK_API_KEY");
        if (StringUtils.hasText(deepSeekApiKey)) {
            return deepSeekApiKey;
        }
        return System.getenv("OPENAI_API_KEY");
    }

    private Map<String, Object> message(String role, String content) {
        return Map.of(
                "role", role,
                "content", content
        );
    }

    private String buildStructuredSystemPrompt(String schema) {
        return """
                你必须输出严格符合要求的 JSON。
                约束：
                1. 只输出 JSON，不要输出 markdown、解释文本或前后缀。
                2. 如果某字段没有内容，输出空数组或空字符串，不要省略字段。
                3. 不要编造输入中不存在的实现和事实。
                4. 你的输出必须满足 json_object 模式，并尽量贴近目标 schema。
                目标 schema：
                %s
                """.formatted(schema);
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String ensureLeadingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "/v1/chat/completions";
        }
        return value.startsWith("/") ? value : "/" + value;
    }
}
