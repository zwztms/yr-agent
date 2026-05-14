package com.yragent.domain.memory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
    private final MemoryZone zone;

    private MemoryFragment(String id, MemoryType type, String title, String content,
                          double priority, Instant createdAt, Instant updatedAt,
                          String taskId, String stage, List<String> tags, MemoryZone zone) {
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
        this.zone = zone;
    }

    public static MemoryFragment create(MemoryType type, String title, String content,
                                        double priority, String taskId, String stage,
                                        List<String> tags) {
        Instant now = Instant.now();
        return new MemoryFragment(
                UUID.randomUUID().toString(), type,
                title != null ? title : "", content,
                Math.max(0.0, Math.min(1.0, priority)),
                now, now, taskId, stage,
                tags != null ? List.copyOf(tags) : List.of(),
                MemoryZone.from(type)
        );
    }

    public static MemoryFragment create(MemoryType type, String title, String content,
                                        double priority, String taskId, String stage,
                                        List<String> tags, MemoryZone zone) {
        Instant now = Instant.now();
        return new MemoryFragment(
                UUID.randomUUID().toString(), type,
                title != null ? title : "", content,
                Math.max(0.0, Math.min(1.0, priority)),
                now, now, taskId, stage,
                tags != null ? List.copyOf(tags) : List.of(),
                zone != null ? zone : MemoryZone.from(type)
        );
    }

    public static MemoryFragment restore(String id, MemoryType type, String title,
                                        String content, double priority,
                                        Instant createdAt, Instant updatedAt,
                                        String taskId, String stage, List<String> tags,
                                        MemoryZone zone) {
        return new MemoryFragment(
                id, type,
                title != null ? title : "", content,
                Math.max(0.0, Math.min(1.0, priority)),
                createdAt, updatedAt, taskId, stage,
                tags != null ? List.copyOf(tags) : List.of(),
                zone
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

    public MemoryZone getZone() {
        return zone != null ? zone : MemoryZone.from(type);
    }
}
