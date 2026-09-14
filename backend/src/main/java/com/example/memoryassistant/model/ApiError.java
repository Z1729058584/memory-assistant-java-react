package com.example.memoryassistant.model;

import java.time.LocalDateTime;

public record ApiError(String message, LocalDateTime timestamp) {}
