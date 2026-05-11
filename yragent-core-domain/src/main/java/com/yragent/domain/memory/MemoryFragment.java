package com.yragent.domain.memory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 记忆片段：记忆系统中统一的持久化对象。不可变，通过工厂方法创建。
public class MemoryFragment {

    private final String id;
    private final MemoryType type;
    private final String title;
    private final String content;
    private final double priority;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String taskId;
    private final String stage;
    private final List<String> tags;

    private MemoryFragment(String id, MemoryType type, String title, String content,
                          double priority, Instant createdAt, Instant updatedAt,
                          String taskId, String stage, List<String> tags) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.content = content;
        this.priority = priority;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.taskId = taskId;
        this.stage = stage;
        this.tags = tags;
    }

    // 新建记忆的工厂方法，自动生成 id 和时间戳。
    public static MemoryFragment create(MemoryType type, String title, String content,
                                        double priority, String taskId, String stage,
                                        List<String> tags) {
        Instant now = Instant.now();
        return new MemoryFragment(
                UUID.randomUUID().toString(),
                type,
                title != null ? title : "",
                content,
                Math.max(0.0, Math.min(1.0, priority)),
                now,
                now,
                taskId,
                stage,
                tags != null ? List.copyOf(tags) : List.of()
        );
    }

    // 从数据库还原已有记忆。
    public static MemoryFragment restore(String id, MemoryType type, String title,
                                        String content, double priority,
                                        Instant createdAt, Instant updatedAt,
                                        String taskId, String stage, List<String> tags) {
        return new MemoryFragment(
                id,
                type,
                title != null ? title : "",
                content,
                Math.max(0.0, Math.min(1.0, priority)),
                createdAt,
                updatedAt,
                taskId,
                stage,
                tags != null ? List.copyOf(tags) : List.of()
        );
    }

    public String getId() {
        return id;
    }

    public MemoryType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public double getPriority() {
        return priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getStage() {
        return stage;
    }

    public List<String> getTags() {
        return tags;
    }
}
