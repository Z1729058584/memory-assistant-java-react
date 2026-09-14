package com.example.memoryassistant.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequest {

    @NotBlank
    private String userId;

    @NotBlank
    private String message;
}
