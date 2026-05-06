package com.yragent.domain.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// MCP 配置属性，绑定 application.yml 中的 yragent.mcp.*。
@Component
@ConfigurationProperties(prefix = "yragent.mcp")
public class McpProperties {

    private boolean enabled = true;
    private List<ServerConfig> servers = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<ServerConfig> getServers() {
        return servers;
    }

    public void setServers(List<ServerConfig> servers) {
        this.servers = servers;
    }

    public static class ServerConfig {

        private String name;
        private String command;
        private List<String> args = new ArrayList<>();
        private Map<String, String> env = new HashMap<>();
        private long timeoutSeconds = 30;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args;
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public void setEnv(Map<String, String> env) {
            this.env = env;
        }

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
