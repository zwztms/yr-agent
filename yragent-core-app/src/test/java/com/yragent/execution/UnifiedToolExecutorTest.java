package com.yragent.execution;

import com.yragent.domain.tool.ToolCapability;
import com.yragent.domain.tool.ToolExecutor.ToolCall;
import com.yragent.domain.tool.ToolExecutor.ToolExecutionResult;
import com.yragent.domain.tool.ToolRiskLevel;
import com.yragent.infrastructure.integration.mcp.McpToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedToolExecutorTest {

    private LocalToolExecutor localToolExecutor;
    private McpToolExecutor mockMcpExecutor;
    private UnifiedToolExecutor unifiedToolExecutor;

    // 模拟 MCP 执行器，直接返回预设结果。
    private static class FakeMcpToolExecutor extends McpToolExecutor {

        private final String serverName;
        private final List<ToolCapability> tools;
        private final String echoOutput;

        FakeMcpToolExecutor(String serverName, List<ToolCapability> tools, String echoOutput) {
            super(new com.yragent.domain.mcp.McpEndpointConfig(
                    serverName, "stdio", "echo", List.of(), java.util.Map.of(), 30));
            this.serverName = serverName;
            this.tools = tools;
            this.echoOutput = echoOutput;
        }

        @Override
        public ToolExecutionResult execute(ToolCall call) {
            for (ToolCapability tool : tools) {
                if (tool.getName().equals(serverName + ":" + call.tool())) {
                    return new ToolExecutionResult(call.tool(), true, echoOutput, "");
                }
            }
            return new ToolExecutionResult(call.tool(), false, "",
                    "MCP Server [" + serverName + "] 不提供工具: " + call.tool());
        }

        @Override
        public List<ToolCapability> getAvailableTools() {
            return tools;
        }

        @Override
        public String getServerName() {
            return serverName;
        }
    }

    @BeforeEach
    void setUp() {
        localToolExecutor = new LocalToolExecutor("workspace");
        // 创建模拟 MCP 执行器，提供 mcp_read 和 mcp_write 两种工具。
        mockMcpExecutor = new FakeMcpToolExecutor(
                "test-server",
                List.of(
                        new ToolCapability("test-server:mcp_read", "远程读取", ToolRiskLevel.READ_ONLY),
                        new ToolCapability("test-server:mcp_write", "远程写入", ToolRiskLevel.MUTATING)
                ),
                "mcp output"
        );
        unifiedToolExecutor = new UnifiedToolExecutor(localToolExecutor, List.of(mockMcpExecutor));
    }

    @Test
    void shouldRouteLocalToolToLocalExecutor() {
        ToolExecutionResult result = unifiedToolExecutor.execute(
                new ToolCall("read_file", java.util.Map.of("path", "test.txt")));

        // read_file 是本地工具，但 test.txt 不存在 → 应该返回本地执行器的错误。
        assertFalse(result.success());
        // 确认错误来自本地执行器（文件不存在），而非"不支持的工具"。
        assertTrue(result.error().contains("NoSuchFileException")
                || result.error().contains("文件") || result.error().contains("test.txt"),
                "错误信息应来自本地执行器: " + result.error());
    }

    @Test
    void shouldRouteUnknownToolToMcpExecutor() {
        // 直接用 MCP 工具名（不带前缀）调用——模拟 LLM 生成的情况。
        ToolExecutionResult result = unifiedToolExecutor.execute(
                new ToolCall("mcp_read", java.util.Map.of("key", "value")));

        // 本地应该返回"不支持"，然后路由到 MCP 执行器。
        assertTrue(result.success(), "应该由 MCP 执行器处理: " + result.error());
        assertEquals("mcp output", result.output());
    }

    @Test
    void shouldTryLocalBeforeMcp() {
        // 这个测试验证本地执行器被优先查询。
        // write_file 是本地工具，但因缺少 content 参数会先失败。
        // 这个结果来自本地执行器，不是 MCP。
        ToolExecutionResult result = unifiedToolExecutor.execute(
                new ToolCall("write_file", java.util.Map.of("path", "test.txt")));

        // 应该返回本地执行器的错误（缺少必需参数 content）。
        assertFalse(result.success());
        assertTrue(result.error().contains("缺少必需参数") || result.error().contains("content"));
    }

    @Test
    void shouldReturnAllToolsFromBothSources() {
        List<ToolCapability> allTools = unifiedToolExecutor.getAllTools();

        // 4 个本地工具 + 2 个 MCP 工具 = 6
        assertEquals(6, allTools.size());

        // 验证本地工具存在。
        assertTrue(allTools.stream().anyMatch(t -> t.getName().equals("read_file")));
        assertTrue(allTools.stream().anyMatch(t -> t.getName().equals("write_file")));

        // 验证 MCP 工具存在。
        assertTrue(allTools.stream().anyMatch(t -> t.getName().equals("test-server:mcp_read")));
        assertTrue(allTools.stream().anyMatch(t -> t.getName().equals("test-server:mcp_write")));
    }

    @Test
    void shouldReturnFailureWhenNoExecutorHandlesTool() {
        ToolExecutionResult result = unifiedToolExecutor.execute(
                new ToolCall("nonexistent_tool", java.util.Map.of()));

        assertFalse(result.success());
    }
}
