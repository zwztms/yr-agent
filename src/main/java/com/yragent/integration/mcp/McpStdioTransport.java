package com.yragent.integration.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// MCP stdio 传输层：管理 MCP Server 子进程，实现 Content-Length 帧协议。
public class McpStdioTransport {

    private static final Logger log = LoggerFactory.getLogger(McpStdioTransport.class);

    private Process process;
    private BufferedWriter stdinWriter;
    private BufferedReader stdoutReader;
    private Thread stderrThread;
    private final long readTimeoutMs;

    public McpStdioTransport(long readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs > 0 ? readTimeoutMs : 30_000;
    }

    // 启动 MCP Server 子进程。
    public void start(String command, List<String> args, Map<String, String> env) {
        try {
            List<String> cmd = new java.util.ArrayList<>();
            cmd.add(command);
            cmd.addAll(args);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            if (env != null && !env.isEmpty()) {
                pb.environment().putAll(env);
            }

            this.process = pb.start();
            this.stdinWriter = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            this.stdoutReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            // 守护线程转发 stderr 到日志，防止缓冲区阻塞。
            this.stderrThread = new Thread(() -> {
                try (BufferedReader stderrReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = stderrReader.readLine()) != null) {
                        log.warn("[MCP stderr] {}", line);
                    }
                } catch (IOException e) {
                    // 进程终止时读取 stderr 会抛异常，忽略。
                }
            }, "mcp-stderr-reader");
            this.stderrThread.setDaemon(true);
            this.stderrThread.start();

            log.info("MCP 子进程已启动: {} {}", command, String.join(" ", args));
        } catch (IOException e) {
            throw new McpConnectionException("无法启动 MCP Server 子进程: " + command, e);
        }
    }

    // 发送 Content-Length 帧格式的消息到子进程 stdin。
    public synchronized void sendMessage(String json) {
        if (stdinWriter == null) {
            throw new McpConnectionException("MCP 传输层未启动");
        }
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            stdinWriter.write("Content-Length: " + bytes.length + "\r\n");
            stdinWriter.write("\r\n");
            stdinWriter.write(json);
            stdinWriter.flush();
        } catch (IOException e) {
            throw new McpConnectionException("向 MCP Server 发送消息失败", e);
        }
    }

    // 从子进程 stdout 接收 Content-Length 帧格式的消息。
    public synchronized String receiveMessage() {
        if (stdoutReader == null) {
            throw new McpConnectionException("MCP 传输层未启动");
        }
        try {
            // 读 Content-Length 头行。
            String headerLine = readLineWithTimeout();
            if (headerLine == null || headerLine.isBlank()) {
                headerLine = readLineWithTimeout(); // 跳过可能的空行
            }
            if (headerLine == null) {
                throw new McpConnectionException("MCP Server 已关闭连接");
            }
            if (!headerLine.startsWith("Content-Length:")) {
                throw new McpConnectionException("无效的 MCP 帧头: " + headerLine);
            }
            int contentLength = Integer.parseInt(headerLine.substring("Content-Length:".length()).trim());

            // 跳过空行（\r\n 分隔符）。
            readLineWithTimeout();

            // 读取指定字节数的 JSON 体。
            char[] buf = new char[contentLength];
            int totalRead = 0;
            long deadline = System.currentTimeMillis() + readTimeoutMs;
            while (totalRead < contentLength) {
                if (System.currentTimeMillis() > deadline) {
                    throw new McpConnectionException("接收 MCP 消息超时（" + readTimeoutMs + "ms）");
                }
                int remaining = contentLength - totalRead;
                int read = stdoutReader.read(buf, totalRead, remaining);
                if (read == -1) {
                    throw new McpConnectionException("MCP Server 在消息接收中途关闭连接");
                }
                totalRead += read;
            }
            return new String(buf, 0, totalRead);
        } catch (McpConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new McpConnectionException("接收 MCP 消息失败", e);
        }
    }

    private String readLineWithTimeout() throws IOException {
        long deadline = System.currentTimeMillis() + readTimeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (stdoutReader.ready()) {
                return stdoutReader.readLine();
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        throw new McpConnectionException("读取 MCP 响应行超时");
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    // 关闭 MCP Server 子进程。
    public void close() {
        if (process == null) {
            return;
        }
        try {
            process.destroy();
            boolean terminated = process.waitFor(2, TimeUnit.SECONDS);
            if (!terminated) {
                process.destroyForcibly();
                log.warn("MCP Server 未能在 2 秒内正常退出，已强制终止");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
        try {
            if (stdinWriter != null) stdinWriter.close();
            if (stdoutReader != null) stdoutReader.close();
        } catch (IOException ignored) {
        }
        log.info("MCP 子进程已关闭");
    }
}
