package com.example.memoryassistant.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemoryUpdateRequest {
    private String category;
    private String key;
    private String value;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confidence;

    private String source;
    private Boolean pinned;
    private String status;
    private LocalDateTime expiresAt;
    private Boolean clearExpiry = false;
}
