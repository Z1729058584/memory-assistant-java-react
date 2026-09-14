package com.example.memoryassistant.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemoryCreateRequest {

    @NotBlank
    private String category;

    @NotBlank
    private String key;

    @NotBlank
    private String value;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confidence = 1.0;

    private String source = "manual";
    private Boolean pinned = false;
    private LocalDateTime expiresAt;
    private Integer expiresInDays;
}
