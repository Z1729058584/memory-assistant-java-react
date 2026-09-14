package com.example.memoryassistant.model;

import java.util.List;

public record ChatResponse(
    String reply,
    List<MemoryOut> usedMemories,
    List<MemoryCandidate> storedMemories,
    String mode,
    String provider,
    String model,
    String error
) {}
