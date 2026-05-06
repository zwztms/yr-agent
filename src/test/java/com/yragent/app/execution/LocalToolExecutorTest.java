package com.yragent.app.execution;

import com.yragent.domain.tool.ToolExecutor.ToolCall;
import com.yragent.domain.tool.ToolExecutor.ToolExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalToolExecutorTest {

    private LocalToolExecutor executor;

    @TempDir
    Path tempWorkspace;

    @BeforeEach
    void setUp() {
        executor = new LocalToolExecutor(tempWorkspace.toString().replace('\\', '/'));
    }

    @Test
    void shouldWriteAndReadFile() {
        String filePath = "test-write-read-" + System.nanoTime() + ".txt";
        String content = "hello from test";

        ToolExecutionResult writeResult = executor.execute(
                new ToolCall("write_file", Map.of("path", filePath, "content", content)));
        assertTrue(writeResult.success(), "write should succeed: " + writeResult.error());

        ToolExecutionResult readResult = executor.execute(
                new ToolCall("read_file", Map.of("path", filePath)));
        assertTrue(readResult.success(), "read should succeed: " + readResult.error());
        assertEquals(content, readResult.output());
    }

    @Test
    void shouldListDirectory() {
        String dir = "test-list-dir-" + System.nanoTime();
        executor.execute(new ToolCall("write_file",
                Map.of("path", dir + "/a.txt", "content", "aaa")));
        executor.execute(new ToolCall("write_file",
                Map.of("path", dir + "/b.txt", "content", "bbb")));

        ToolExecutionResult result = executor.execute(
                new ToolCall("list_dir", Map.of("path", dir)));

        assertTrue(result.success(), "list should succeed: " + result.error());
        assertTrue(result.output().contains("a.txt"));
        assertTrue(result.output().contains("b.txt"));
    }

    @Test
    void shouldRejectPathTraversal() {
        ToolExecutionResult result = executor.execute(
                new ToolCall("read_file", Map.of("path", "../outside.txt")));

        assertFalse(result.success());
        assertTrue(result.error().contains("路径穿越"));
    }

    @Test
    void shouldRejectAbsolutePathOutsideWorkspace() {
        ToolExecutionResult result = executor.execute(
                new ToolCall("read_file", Map.of("path", "/etc/passwd")));

        assertFalse(result.success());
        assertTrue(result.error().contains("必须在") || result.error().contains("路径穿越"));
    }

    @Test
    void shouldRejectUnknownTool() {
        ToolExecutionResult result = executor.execute(
                new ToolCall("delete_all_files", Map.of()));

        assertFalse(result.success());
        assertTrue(result.error().contains("不支持的工具"));
    }
}
