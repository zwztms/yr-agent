package com.yragent.domain.mcp;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class McpRegistry {

    private final McpProperties mcpProperties;

    public McpRegistry(McpProperties mcpProperties) {
        this.mcpProperties = mcpProperties;
    }

    public List<McpEndpointConfig> listEnabled() {
        if (!mcpProperties.isEnabled() || mcpProperties.getServers() == null) {
            return List.of();
        }
        return mcpProperties.getServers().stream()
                .map(s -> new McpEndpointConfig(
                        s.getName(),
                        "stdio",
                        s.getCommand(),
                        s.getArgs(),
                        s.getEnv(),
                        s.getTimeoutSeconds()))
                .toList();
    }
}
