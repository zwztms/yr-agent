package com.yragent.domain.memory;

import com.yragent.domain.stage.StageType;

import java.time.Instant;

public record ConversationMessage(
        String role,
        String content,
        Instant timestamp,
        StageType stage
) {
    public static ConversationMessage create(String role, String content, StageType stage) {
        return new ConversationMessage(role, content, Instant.now(), stage);
    }
}
