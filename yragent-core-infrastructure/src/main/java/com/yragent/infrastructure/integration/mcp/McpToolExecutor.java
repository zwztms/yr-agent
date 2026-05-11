package com.yragent.infrastructure.integration.mcp;

import com.yragent.domain.mcp.McpEndpointConfig;
import com.yragent.domain.tool.ToolCapability;
import com.yragent.domain.tool.ToolExecutor;
import com.yragent.domain.tool.ToolRiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// MCP 工具执行器：管理单个 MCP Server 的完整生命周期。
// 实现 ToolExecutor 接口，对上层透明。
public class McpToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(McpToolExecutor.class);

    private final McpEndpointConfig config;
    private final McpJsonRpcProtocol protocol;
    private final McpStdioTransport transport;
    private final Map<String, McpJsonRpcProtocol.ToolDef> toolCache;
    private final AtomicBoolean initialized;
    private volatile boolean permanentlyBroken;

    public McpToolExecutor(McpEndpointConfig config) {
        this.config = config;
        this.protocol = new McpJsonRpcProtocol();
        this.transport = new McpStdioTransport(config.getTimeoutSeconds() * 1000);
        this.toolCache = new ConcurrentHashMap<>();
        this.initialized = new AtomicBoolean(false);
        this.permanentlyBroken = false;
    }

    // 初始化：启动子进程 → initialize 握手 → tools/list → 缓存。
    public void initialize() {
        if (initialized.get() || permanentlyBroken) {
            return;
        }
        synchronized (this) {
            if (initialized.get() || permanentlyBroken) {
                return;
            }
            try {
                transport.start(config.getCommand(), config.getArgs(), config.getEnv());

                // initialize 握手。
                String initRequest = protocol.buildInitializeRequest("yragent", "1.0.0");
                transport.sendMessage(initRequest);
                String initResponse = transport.receiveMessage();
                protocol.parseResponse(initResponse);

                // 发送 initialized 通知。
                transport.sendMessage(protocol.buildInitializedNotification());

                // 获取工具列表。
                String toolsRequest = protocol.buildToolsListRequest();
                transport.sendMessage(toolsRequest);
                String toolsResponse = transport.receiveMessage();
                var result = protocol.parseResponse(toolsResponse);
                List<McpJsonRpcProtocol.ToolDef> toolDefs = protocol.parseToolsListResult(result);

                toolCache.clear();
                for (var def : toolDefs) {
                    toolCache.put(def.name(), def);
                }
                initialized.set(true);
                log.info("MCP Server [{}] 初始化完成, 发现 {} 个工具", config.getName(), toolCache.size());
            } catch (Exception e) {
                log.error("MCP Server [{}] 初始化失败", config.getName(), e);
                transport.close();
                permanentlyBroken = true;
            }
        }
    }

    @Override
    public ToolExecutionResult execute(ToolCall call) {
        if (permanentlyBroken) {
            return new ToolExecutionResult(call.tool(), false, "",
                    "MCP Server [" + config.getName() + "] 不可用");
        }
        if (!initialized.get()) {
            initialize();
            if (!initialized.get()) {
                return new ToolExecutionResult(call.tool(), false, "",
                        "MCP Server [" + config.getName() + "] 初始化失败");
            }
        }

        var toolDef = toolCache.get(call.tool());
        if (toolDef == null) {
            return new ToolExecutionResult(call.tool(), false, "",
                    "MCP Server [" + config.getName() + "] 不提供工具: " + call.tool());
        }

        try {
            Map<String, Object> arguments = new java.util.HashMap<>(call.params());
            String request = protocol.buildToolsCallRequest(call.tool(), arguments);
            transport.sendMessage(request);
            String response = transport.receiveMessage();
            var result = protocol.parseResponse(response);
            String textContent = protocol.extractTextContent(result);
            return new ToolExecutionResult(call.tool(), true, textContent, "");
        } catch (Exception e) {
            log.warn("MCP Server [{}] 工具调用失败: tool={}", config.getName(), call.tool(), e);
            // 进程死了则尝试一次重启。
            if (!transport.isAlive()) {
                log.info("MCP Server [{}] 进程已死, 尝试重启", config.getName());
                initialized.set(false);
                transport.close();
                initialize();
            }
            return new ToolExecutionResult(call.tool(), false, "",
                    "MCP 工具调用失败: " + e.getMessage());
        }
    }

    // 返回此 MCP Server 提供的所有工具。
    public List<ToolCapability> getAvailableTools() {
        if (!initialized.get() && !permanentlyBroken) {
            initialize();
        }
        if (toolCache.isEmpty()) {
            return List.of();
        }
        List<ToolCapability> capabilities = new ArrayList<>();
        for (var def : toolCache.values()) {
            // 根据工具名推断风险等级：含 read/list/get → READ_ONLY，含 exec/run/delete → DANGEROUS，其余 MUTATING。
            ToolRiskLevel riskLevel = classifyRisk(def.name(), def.description());
            capabilities.add(new ToolCapability(
                    config.getName() + ":" + def.name(),
                    def.description() != null ? def.description() : def.name(),
                    riskLevel));
        }
        return capabilities;
    }

    private ToolRiskLevel classifyRisk(String name, String description) {
        String combined = (name + " " + (description != null ? description : "")).toLowerCase();
        if (combined.contains("delete") || combined.contains("exec") || combined.contains("run")) {
            return ToolRiskLevel.DANGEROUS;
        }
        if (combined.contains("write") || combined.contains("create") || combined.contains("update")) {
            return ToolRiskLevel.MUTATING;
        }
        return ToolRiskLevel.READ_ONLY;
    }

    public String getServerName() {
        return config.getName();
    }

    public void close() {
        transport.close();
    }
}
