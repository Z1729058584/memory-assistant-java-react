package com.example.memoryassistant.model;

import java.util.List;
import java.util.Map;

public record MemoryReviewOut(
    MemoryReviewStats stats,
    Map<String, Integer> byCategory,
    List<MemoryOut> staleMemories,
    List<MemoryOut> expiringSoonMemories
) {}
