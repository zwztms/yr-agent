package com.yragent.integration.mcp;

import com.yragent.domain.mcp.McpEndpointConfig;
import org.springframework.stereotype.Component;

@Component
public class McpClientFactory {

    public McpToolExecutor create(McpEndpointConfig config) {
        return new McpToolExecutor(config);
    }
}
