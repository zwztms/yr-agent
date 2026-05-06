package com.yragent.integration.mcp;

import com.yragent.domain.mcp.McpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

// MCP 自动配置：仅在 yragent.mcp.enabled=true 时激活。
@Configuration
@ConditionalOnProperty(prefix = "yragent.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpAutoConfiguration.class);

    private final McpProperties mcpProperties;
    private final McpClientFactory mcpClientFactory;

    public McpAutoConfiguration(McpProperties mcpProperties, McpClientFactory mcpClientFactory) {
        this.mcpProperties = mcpProperties;
        this.mcpClientFactory = mcpClientFactory;
    }

    @Bean
    List<McpToolExecutor> mcpToolExecutors() {
        var servers = mcpProperties.getServers();
        if (servers == null || servers.isEmpty()) {
            log.info("未配置 MCP Server，跳过 MCP 工具执行器创建");
            return List.of();
        }
        List<McpToolExecutor> executors = new ArrayList<>();
        for (var serverConfig : servers) {
            if (serverConfig.getCommand() == null || serverConfig.getCommand().isBlank()) {
                log.warn("MCP Server [{}] 缺少 command 配置，跳过", serverConfig.getName());
                continue;
            }
            var config = createEndpointConfig(serverConfig);
            executors.add(mcpClientFactory.create(config));
            log.info("已注册 MCP Server [{}]: {} {}", serverConfig.getName(),
                    serverConfig.getCommand(), String.join(" ", serverConfig.getArgs()));
        }
        return executors;
    }

    private com.yragent.domain.mcp.McpEndpointConfig createEndpointConfig(
            McpProperties.ServerConfig sc) {
        return new com.yragent.domain.mcp.McpEndpointConfig(
                sc.getName(),
                "stdio",
                sc.getCommand(),
                sc.getArgs() != null ? List.copyOf(sc.getArgs()) : List.of(),
                sc.getEnv() != null ? java.util.Map.copyOf(sc.getEnv()) : java.util.Map.of(),
                sc.getTimeoutSeconds());
    }
}
