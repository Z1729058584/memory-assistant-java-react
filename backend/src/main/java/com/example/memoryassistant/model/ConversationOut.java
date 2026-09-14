package com.example.memoryassistant.model;

import com.example.memoryassistant.entity.ConversationTurnEntity;
import java.time.LocalDateTime;

public record ConversationOut(Long id, String userId, String role, String content, LocalDateTime createdAt) {
    public static ConversationOut fromEntity(ConversationTurnEntity entity) {
        return new ConversationOut(
            entity.getId(),
            entity.getUserId(),
            entity.getRole(),
            entity.getContent(),
            entity.getCreatedAt()
        );
    }
}
