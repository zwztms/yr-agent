package com.yragent.domain.memory;

// 记忆类型：统一区分记忆系统中不同用途的记忆。
// @deprecated 使用 MemoryZone 替代。旧数据保持兼容，新代码优先用 MemoryZone。
@Deprecated
public enum MemoryType {
    USER_PREFERENCE,
    PROJECT_POLICY,
    TASK_STATE,
    DECISION,
    FAILURE_PATTERN,
    GATE_ATTEMPT
}
