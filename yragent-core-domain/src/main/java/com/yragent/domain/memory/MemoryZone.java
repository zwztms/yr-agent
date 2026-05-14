package com.yragent.domain.memory;

public enum MemoryZone {
    PREFERENCE,
    EXPERIENCE,
    DECISION,
    ENTITY;

    public static MemoryZone from(MemoryType type) {
        if (type == null) return null;
        return switch (type) {
            case USER_PREFERENCE, PROJECT_POLICY -> PREFERENCE;
            case FAILURE_PATTERN -> EXPERIENCE;
            case DECISION, GATE_ATTEMPT -> DECISION;
            case TASK_STATE -> ENTITY;
        };
    }
}
