package com.yragent.execution;

import com.yragent.domain.tool.ToolCapability;
import com.yragent.domain.tool.ToolExecutor;
import com.yragent.domain.tool.ToolRiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// 基于 Java 标准库的本地工具执行器。
// 只支持白名单内的四种工具，所有操作限制在 workspaceRoot 目录内。
@Component
public class LocalToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(LocalToolExecutor.class);

    private static final long COMMAND_TIMEOUT_SECONDS = 10;

    private final Path workspaceRoot;

    public LocalToolExecutor(@org.springframework.beans.factory.annotation.Value("${yragent.workspace-root:E:/xiangmu}") String workspaceRoot) {
        this.workspaceRoot = Paths.get(workspaceRoot).toAbsolutePath().normalize();
    }

    @Override
    public String getWorkspaceRoot() {
        return workspaceRoot.toString();
    }

    @Override
    public ToolExecutionResult execute(ToolCall call) {
        try {
            return switch (call.tool()) {
                case "read_file" -> executeReadFile(call.params());
                case "write_file" -> executeWriteFile(call.params());
                case "list_dir" -> executeListDir(call.params());
                case "run_command" -> executeRunCommand(call.params());
                default -> new ToolExecutionResult(call.tool(), false, "",
                        "不支持的工具: " + call.tool());
            };
        } catch (SecurityException e) {
            return new ToolExecutionResult(call.tool(), false, "", e.getMessage());
        } catch (Exception e) {
            log.error("工具执行异常: tool={}", call.tool(), e);
            return new ToolExecutionResult(call.tool(), false, "", e.getMessage());
        }
    }

    private ToolExecutionResult executeReadFile(Map<String, String> params) throws IOException {
        String path = requireParam(params, "path");
        Path resolved = resolvePath(path);
        String content = Files.readString(resolved);
        return new ToolExecutionResult("read_file", true, content, "");
    }

    private ToolExecutionResult executeWriteFile(Map<String, String> params) throws IOException {
        String path = requireParam(params, "path");
        String content = requireParam(params, "content");
        Path resolved = resolvePath(path);
        Files.createDirectories(resolved.getParent());
        Files.writeString(resolved, content);
        return new ToolExecutionResult("write_file", true,
                "写入成功: " + resolved + " (" + content.length() + " 字符)", "");
    }

    private ToolExecutionResult executeListDir(Map<String, String> params) throws IOException {
        String path = params.getOrDefault("path", ".");
        Path resolved = resolvePath(path);
        if (!Files.isDirectory(resolved)) {
            return new ToolExecutionResult("list_dir", false, "",
                    "路径不是目录: " + resolved);
        }
        String listing = Files.list(resolved)
                .map(p -> p.getFileName().toString())
                .collect(Collectors.joining("\n"));
        return new ToolExecutionResult("list_dir", true, listing, "");
    }

    private ToolExecutionResult executeRunCommand(Map<String, String> params) throws Exception {
        String command = requireParam(params, "command");
        String workingDir = params.getOrDefault("workingDir", ".");
        Path resolvedWorkingDir = resolvePath(workingDir);

        ProcessBuilder pb = new ProcessBuilder();
        if (isWindows()) {
            pb.command("cmd.exe", "/c", command);
        } else {
            pb.command("sh", "-c", command);
        }
        pb.directory(resolvedWorkingDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new ToolExecutionResult("run_command", false, "",
                    "命令超时（" + COMMAND_TIMEOUT_SECONDS + " 秒）: " + command);
        }

        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.exitValue();
        boolean success = exitCode == 0;
        return new ToolExecutionResult("run_command", success, output,
                success ? "" : "命令退出码: " + exitCode);
    }

    // 将用户提供的路径解析到 workspaceRoot 目录下，拒绝路径穿越。
    // 支持相对路径（相对于 workspaceRoot）和绝对路径（必须在 workspaceRoot 下）。
    private Path resolvePath(String userPath) {
        String normalized = userPath.replace('\\', '/');
        Path resolved;
        if (normalized.startsWith("/") || normalized.contains(":")) {
            // 绝对路径：必须在 workspaceRoot 之下。
            resolved = Paths.get(normalized).normalize();
        } else {
            // 相对路径：以 workspaceRoot 为基准解析。
            resolved = workspaceRoot.resolve(normalized).normalize();
        }
        if (!resolved.startsWith(workspaceRoot)) {
            throw new SecurityException("路径穿越被拒绝: " + userPath + "（必须在 " + workspaceRoot + " 目录下）");
        }
        return resolved;
    }

    private String requireParam(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必需参数: " + key);
        }
        return value;
    }

    // 返回本地内置工具的定义列表，供 ToolRegistry 汇总。
    public List<ToolCapability> getAvailableTools() {
        return List.of(
                new ToolCapability("read_file", "读取文件内容", ToolRiskLevel.READ_ONLY),
                new ToolCapability("write_file", "写入文件内容", ToolRiskLevel.MUTATING),
                new ToolCapability("list_dir", "列出目录内容", ToolRiskLevel.READ_ONLY),
                new ToolCapability("run_command", "执行 Shell 命令", ToolRiskLevel.DANGEROUS)
        );
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
