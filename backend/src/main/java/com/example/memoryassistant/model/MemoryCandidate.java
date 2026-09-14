package com.example.memoryassistant.model;

import java.time.LocalDateTime;

public record MemoryCandidate(
    String category,
    String key,
    String value,
    double confidence,
    String source,
    boolean pinned,
    LocalDateTime expiresAt
) {}
