package com.example.memoryassistant.model;

public record MemoryReviewStats(
    int total,
    int active,
    int pinned,
    int expired,
    int archived,
    int stale,
    int expiringSoon
) {}
