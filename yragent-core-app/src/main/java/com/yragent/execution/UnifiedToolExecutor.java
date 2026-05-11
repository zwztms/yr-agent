package com.yragent.execution;

import com.yragent.domain.tool.ToolCapability;
import com.yragent.domain.tool.ToolExecutor;
import com.yragent.infrastructure.integration.mcp.McpToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 统一工具执行器：组合 LocalToolExecutor 和所有 McpToolExecutor。
// @Primary 确保 Spring 注入 ToolExecutor 时优先使用此实现。
@Primary
@Component
public class UnifiedToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(UnifiedToolExecutor.class);

    private final LocalToolExecutor localToolExecutor;
    private final List<McpToolExecutor> mcpToolExecutors;

    public UnifiedToolExecutor(LocalToolExecutor localToolExecutor,
                               List<McpToolExecutor> mcpToolExecutors) {
        this.localToolExecutor = localToolExecutor;
        this.mcpToolExecutors = mcpToolExecutors != null ? List.copyOf(mcpToolExecutors) : List.of();
    }

    @Override
    public ToolExecutionResult execute(ToolCall call) {
        // 优先本地执行器，速度快且无网络依赖。
        ToolExecutionResult result = localToolExecutor.execute(call);
        if (!result.success() && result.error().contains("不支持的工具")) {
            // 本地不识别，遍历 MCP 执行器。
            for (McpToolExecutor mcp : mcpToolExecutors) {
                // MCP 工具名格式为 "serverName:toolName"，这里支持两种格式。
                result = mcp.execute(call);
                if (result.success()) {
                    break;
                }
            }
        }
        return result;
    }

    // 返回所有可用工具（本地 + 所有 MCP Server）。
    public List<ToolCapability> getAllTools() {
        List<ToolCapability> allTools = new ArrayList<>(localToolExecutor.getAvailableTools());
        for (McpToolExecutor mcp : mcpToolExecutors) {
            try {
                allTools.addAll(mcp.getAvailableTools());
            } catch (Exception e) {
                log.warn("获取 MCP Server [{}] 工具列表失败", mcp.getServerName(), e);
            }
        }
        return allTools;
    }
}
