package com.yragent.infrastructure.integration.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpJsonRpcProtocolTest {

    private McpJsonRpcProtocol protocol;

    @BeforeEach
    void setUp() {
        protocol = new McpJsonRpcProtocol();
    }

    @Test
    void shouldBuildValidInitializeRequest() {
        String json = protocol.buildInitializeRequest("yragent", "1.0.0");

        assertTrue(json.contains("\"method\":\"initialize\""));
        assertTrue(json.contains("\"protocolVersion\":\"2025-03-26\""));
        assertTrue(json.contains("\"clientInfo\""));
        assertTrue(json.contains("\"yragent\""));
    }

    @Test
    void shouldBuildValidToolsListRequest() {
        String json = protocol.buildToolsListRequest();

        assertTrue(json.contains("\"method\":\"tools/list\""));
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(json.contains("\"id\""));
    }

    @Test
    void shouldBuildValidToolsCallRequest() {
        String json = protocol.buildToolsCallRequest("read-file",
                Map.of("path", "/tmp/test.txt"));

        assertTrue(json.contains("\"method\":\"tools/call\""));
        assertTrue(json.contains("\"name\":\"read-file\""));
        assertTrue(json.contains("\"path\":\"/tmp/test.txt\""));
    }

    @Test
    void shouldBuildInitializedNotification() {
        String json = protocol.buildInitializedNotification();

        assertTrue(json.contains("notifications/initialized"));
        // 通知不应该有 id 字段。
        assertTrue(!json.contains("\"id\""));
    }

    @Test
    void shouldParseSuccessfulResponse() {
        String response = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "protocolVersion": "2024-11-05",
                    "serverInfo": {"name": "test-server", "version": "1.0.0"}
                  }
                }""";

        JsonNode result = protocol.parseResponse(response);
        assertNotNull(result);
        assertEquals("2024-11-05", result.path("protocolVersion").asText());
    }

    @Test
    void shouldThrowOnErrorResponse() {
        String errorResponse = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "error": {
                    "code": -32601,
                    "message": "Method not found"
                  }
                }""";

        assertThrows(McpConnectionException.class, () -> {
            protocol.parseResponse(errorResponse);
        });
    }

    @Test
    void shouldParseToolsListResult() {
        String response = """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "result": {
                    "tools": [
                      {
                        "name": "read-file",
                        "description": "Read a file",
                        "inputSchema": {
                          "type": "object",
                          "properties": {
                            "path": {"type": "string"}
                          },
                          "required": ["path"]
                        }
                      },
                      {
                        "name": "write-file",
                        "description": "Write a file",
                        "inputSchema": {
                          "type": "object",
                          "properties": {
                            "path": {"type": "string"},
                            "content": {"type": "string"}
                          }
                        }
                      }
                    ]
                  }
                }""";

        JsonNode result = protocol.parseResponse(response);
        List<McpJsonRpcProtocol.ToolDef> tools = protocol.parseToolsListResult(result);

        assertEquals(2, tools.size());
        assertEquals("read-file", tools.get(0).name());
        assertEquals("Read a file", tools.get(0).description());
        assertTrue(tools.get(0).inputSchema().containsKey("properties"));
        assertEquals("write-file", tools.get(1).name());
    }

    @Test
    void shouldHandleEmptyToolsList() {
        String response = """
                {"jsonrpc":"2.0","id":2,"result":{"tools":[]}}""";

        JsonNode result = protocol.parseResponse(response);
        List<McpJsonRpcProtocol.ToolDef> tools = protocol.parseToolsListResult(result);

        assertTrue(tools.isEmpty());
    }

    @Test
    void shouldExtractTextContent() {
        String response = """
                {
                  "jsonrpc": "2.0",
                  "id": 3,
                  "result": {
                    "content": [
                      {"type": "text", "text": "Hello from MCP server"},
                      {"type": "text", "text": "Second line"}
                    ],
                    "isError": false
                  }
                }""";

        JsonNode result = protocol.parseResponse(response);
        String text = protocol.extractTextContent(result);

        assertEquals("Hello from MCP serverSecond line", text);
    }
}
