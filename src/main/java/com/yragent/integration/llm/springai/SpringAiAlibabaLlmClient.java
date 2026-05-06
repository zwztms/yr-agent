package com.yragent.integration.llm.springai;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yragent.domain.model.LlmClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "yragent.llm", name = "provider", havingValue = "spring-ai-alibaba")
public class SpringAiAlibabaLlmClient implements LlmClient {

    private static final String DEFAULT_SYSTEM_PROMPT = "你是 yragent 的模型调用实现，请严格遵守用户输入和输出约束。";

    private final String modelName;
    private final String configuredApiKey;
    private final String configuredBaseUrl;
    private volatile ChatClient chatClient;

    public SpringAiAlibabaLlmClient(@Value("${yragent.llm.executor-model:qwen-plus}") String modelName,
                                    @Value("${yragent.llm.api-key:}") String configuredApiKey,
                                    @Value("${yragent.llm.base-url:}") String configuredBaseUrl) {
        this.modelName = modelName;
        this.configuredApiKey = configuredApiKey;
        this.configuredBaseUrl = configuredBaseUrl;
    }

    @Override
    public String chatCompletion(String prompt) {
        return getChatClient()
                .prompt(prompt)
                .call()
                .content();
    }

    @Override
    public String structuredCompletion(String prompt, String schema) {
        return getChatClient()
                .prompt()
                .system(buildStructuredSystemPrompt(schema))
                .user(prompt)
                .call()
                .content();
    }

    private ChatClient getChatClient() {
        if (chatClient == null) {
            synchronized (this) {
                if (chatClient == null) {
                    chatClient = createChatClient();
                }
            }
        }
        return chatClient;
    }

    private ChatClient createChatClient() {
        String apiKey = resolveApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new UnsupportedOperationException("DashScope API key is not configured");
        }

        DashScopeApi.Builder apiBuilder = DashScopeApi.builder().apiKey(apiKey);
        if (StringUtils.hasText(configuredBaseUrl)) {
            apiBuilder.baseUrl(configuredBaseUrl);
        }

        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(modelName)
                .withTopP(0.7)
                .build();

        DashScopeChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(apiBuilder.build())
                .defaultOptions(options)
                .build();

        return ChatClient.builder(chatModel)
                .defaultSystem(DEFAULT_SYSTEM_PROMPT)
                .build();
    }

    private String resolveApiKey() {
        if (StringUtils.hasText(configuredApiKey)) {
            return configuredApiKey;
        }
        return System.getenv("AI_DASHSCOPE_API_KEY");
    }

    private String buildStructuredSystemPrompt(String schema) {
        return """
                你必须输出严格符合要求的 JSON。
                约束：
                1. 只输出 JSON，不要输出 markdown、解释文本或前后缀。
                2. 如果某字段没有内容，输出空数组或空字符串，不要省略字段。
                3. 不要编造输入中不存在的实现和事实。
                目标 schema：
                %s
                """.formatted(schema);
    }
}
