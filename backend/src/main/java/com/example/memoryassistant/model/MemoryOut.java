package com.example.memoryassistant.model;

import com.example.memoryassistant.entity.MemoryEntity;
import java.time.LocalDateTime;

public record MemoryOut(
    Long id,
    String userId,
    String category,
    String key,
    String value,
    double confidence,
    String source,
    boolean pinned,
    String status,
    int accessCount,
    LocalDateTime lastAccessedAt,
    LocalDateTime expiresAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static MemoryOut fromEntity(MemoryEntity entity) {
        return new MemoryOut(
            entity.getId(),
            entity.getUserId(),
            entity.getCategory(),
            entity.getMemoryKey(),
            entity.getValue(),
            entity.getConfidence() == null ? 1.0 : entity.getConfidence(),
            entity.getSource(),
            Boolean.TRUE.equals(entity.getPinned()),
            entity.getStatus(),
            entity.getAccessCount() == null ? 0 : entity.getAccessCount(),
            entity.getLastAccessedAt(),
            entity.getExpiresAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
