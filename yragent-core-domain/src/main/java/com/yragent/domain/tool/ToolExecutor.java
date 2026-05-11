package com.yragent.domain.tool;

import java.util.Map;

// 工具执行器接口：执行一项工具调用并返回结构化结果。
public interface ToolExecutor {

    ToolExecutionResult execute(ToolCall call);

    // 返回工作区根目录，供阶段提示词使用。
    default String getWorkspaceRoot() {
        return "E:/xiangmu";
    }

    // 单次工具调用。
    record ToolCall(String tool, Map<String, String> params) {}

    // 单次工具调用结果。
    record ToolExecutionResult(String tool, boolean success, String output, String error) {}
}
