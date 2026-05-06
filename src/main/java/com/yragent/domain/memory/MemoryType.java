package com.yragent.domain.memory;

// 记忆类型：统一区分记忆系统中不同用途的记忆。
public enum MemoryType {
    USER_PREFERENCE,    // 开发者偏好（风险偏好、确认模式、语言偏好等）
    PROJECT_POLICY,     // 项目策略（目录禁区、测试命令、代码规范等）
    TASK_STATE,         // 任务状态（当前阶段、进度、已执行动作等）
    DECISION,           // 决策记录（决策主题、内容、原因、来源等）
    FAILURE_PATTERN,    // 失败模式（失败原因、场景、解决方案、发生次数等）
    GATE_ATTEMPT        // 门禁尝试历史（每轮门禁的完整快照）
}
