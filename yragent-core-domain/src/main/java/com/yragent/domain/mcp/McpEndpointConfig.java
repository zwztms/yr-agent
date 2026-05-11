package com.yragent.domain.mcp;

import java.util.List;
import java.util.Map;

// MCP 端点配置：描述如何连接一个 MCP Server。不可变对象。
public class McpEndpointConfig {

    private final String name;
    private final String transport;
    private final String command;
    private final List<String> args;
    private final Map<String, String> env;
    private final long timeoutSeconds;

    public McpEndpointConfig(String name, String transport, String command,
                             List<String> args, Map<String, String> env,
                             long timeoutSeconds) {
        this.name = name;
        this.transport = transport;
        this.command = command;
        this.args = args != null ? List.copyOf(args) : List.of();
        this.env = env != null ? Map.copyOf(env) : Map.of();
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
    }

    public String getName() {
        return name;
    }

    public String getTransport() {
        return transport;
    }

    public String getCommand() {
        return command;
    }

    public List<String> getArgs() {
        return args;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
