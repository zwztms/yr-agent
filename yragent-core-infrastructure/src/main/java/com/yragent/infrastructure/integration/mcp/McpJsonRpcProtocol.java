package com.yragent.infrastructure.integration.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

// MCP JSON-RPC 2.0 协议消息构建和解析器。
public class McpJsonRpcProtocol {

    private static final Logger log = LoggerFactory.getLogger(McpJsonRpcProtocol.class);

    private final ObjectMapper objectMapper;
    private final AtomicLong requestIdCounter;

    public McpJsonRpcProtocol() {
        this.objectMapper = new ObjectMapper();
        this.requestIdCounter = new AtomicLong(1);
    }

    // ---- 请求构建 ----

    public String buildInitializeRequest(String clientName, String clientVersion) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.put("id", requestIdCounter.getAndIncrement());
        root.put("method", "initialize");
        ObjectNode params = root.putObject("params");
        params.put("protocolVersion", "2025-03-26");
        params.putObject("capabilities");
        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", clientName);
        clientInfo.put("version", clientVersion);
        return root.toString();
    }

    public String buildInitializedNotification() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.put("method", "notifications/initialized");
        return root.toString();
    }

    public String buildToolsListRequest() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.put("id", requestIdCounter.getAndIncrement());
        root.put("method", "tools/list");
        root.putObject("params");
        return root.toString();
    }

    public String buildToolsCallRequest(String toolName, Map<String, Object> arguments) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.put("id", requestIdCounter.getAndIncrement());
        root.put("method", "tools/call");
        ObjectNode params = root.putObject("params");
        params.put("name", toolName);
        params.set("arguments", objectMapper.valueToTree(arguments));
        return root.toString();
    }

    // ---- 响应解析 ----

    // 从 JSON-RPC 响应中提取 result 节点。若存在 error 节点则抛异常。
    public JsonNode parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("error")) {
                JsonNode error = root.get("error");
                String msg = error.has("message") ? error.get("message").asText() : "Unknown error";
                throw new McpConnectionException("MCP JSON-RPC error: " + msg);
            }
            return root.get("result");
        } catch (McpConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new McpConnectionException("Failed to parse MCP response: " + json, e);
        }
    }

    // 从 tools/list 响应中提取工具定义列表。
    public List<ToolDef> parseToolsListResult(JsonNode result) {
        List<ToolDef> tools = new ArrayList<>();
        if (result == null || !result.has("tools")) {
            return tools;
        }
        JsonNode toolsNode = result.get("tools");
        if (!toolsNode.isArray()) {
            return tools;
        }
        for (JsonNode toolNode : toolsNode) {
            String name = toolNode.path("name").asText("");
            String description = toolNode.path("description").asText("");
            Map<String, Object> inputSchema = jsonNodeToMap(toolNode.path("inputSchema"));
            if (!name.isBlank()) {
                tools.add(new ToolDef(name, description, inputSchema));
            }
        }
        return tools;
    }

    // 从 tools/call 响应中提取文本内容。
    public String extractTextContent(JsonNode result) {
        if (result == null || !result.has("content")) {
            return "";
        }
        JsonNode content = result.get("content");
        if (!content.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : content) {
            String type = block.path("type").asText("");
            if ("text".equals(type)) {
                sb.append(block.path("text").asText(""));
            }
        }
        return sb.toString();
    }

    // 将 JsonNode 递归转为 Map<String, Object>。
    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        Map<String, Object> map = new HashMap<>();
        if (node == null || !node.isObject()) {
            return map;
        }
        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            map.put(field.getKey(), jsonNodeToValue(field.getValue()));
        }
        return map;
    }

    private Object jsonNodeToValue(JsonNode node) {
        if (node.isNull()) return null;
        if (node.isBoolean()) return node.asBoolean();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode item : node) {
                list.add(jsonNodeToValue(item));
            }
            return list;
        }
        if (node.isObject()) return jsonNodeToMap(node);
        return node.asText();
    }

    // MCP 工具定义。
    public record ToolDef(String name, String description, Map<String, Object> inputSchema) {}
}
