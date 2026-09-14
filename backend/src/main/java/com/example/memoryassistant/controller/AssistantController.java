package com.example.memoryassistant.controller;

import com.example.memoryassistant.model.AppConfigOut;
import com.example.memoryassistant.model.ChatRequest;
import com.example.memoryassistant.model.ChatResponse;
import com.example.memoryassistant.model.ConversationOut;
import com.example.memoryassistant.model.MemoryCandidate;
import com.example.memoryassistant.model.MemoryCreateRequest;
import com.example.memoryassistant.model.MemoryOut;
import com.example.memoryassistant.model.MemoryReviewOut;
import com.example.memoryassistant.model.MemoryUpdateRequest;
import com.example.memoryassistant.service.AssistantService;
import com.example.memoryassistant.service.MemoryService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AssistantController {

    private static final String AI_MODEL_HEADER = "x-ai-model";

    private final AssistantService assistantService;
    private final MemoryService memoryService;

    public AssistantController(AssistantService assistantService, MemoryService memoryService) {
        this.assistantService = assistantService;
        this.memoryService = memoryService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/app-config")
    public AppConfigOut appConfig(@RequestHeader(value = AI_MODEL_HEADER, required = false) String requestedModel) {
        return assistantService.appConfig(requestedModel);
    }

    @PostMapping("/chat")
    public ChatResponse chat(
        @Valid @RequestBody ChatRequest request,
        @RequestHeader(value = AI_MODEL_HEADER, required = false) String requestedModel
    ) {
        return assistantService.chat(request, requestedModel);
    }

    @GetMapping("/users/{userId}/memories")
    public List<MemoryOut> listMemories(
        @PathVariable String userId,
        @RequestParam(defaultValue = "false") boolean includeInactive,
        @RequestParam(defaultValue = "false") boolean includeExpired
    ) {
        return memoryService.listMemories(userId, includeInactive, includeExpired)
            .stream()
            .map(MemoryOut::fromEntity)
            .toList();
    }

    @PostMapping("/users/{userId}/memories")
    public ResponseEntity<MemoryOut> createMemory(
        @PathVariable String userId,
        @Valid @RequestBody MemoryCreateRequest request
    ) {
        LocalDateTime expiresAt = request.getExpiresAt();
        if (expiresAt == null && request.getExpiresInDays() != null) {
            expiresAt = LocalDateTime.now().plusDays(request.getExpiresInDays());
        }

        MemoryOut out = MemoryOut.fromEntity(memoryService.createMemory(
            userId,
            new MemoryCandidate(
                request.getCategory(),
                request.getKey(),
                request.getValue(),
                request.getConfidence() == null ? 1.0 : request.getConfidence(),
                request.getSource(),
                Boolean.TRUE.equals(request.getPinned()),
                expiresAt
            )
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    @PatchMapping("/users/{userId}/memories/{memoryId}")
    public MemoryOut updateMemory(
        @PathVariable String userId,
        @PathVariable Long memoryId,
        @Valid @RequestBody MemoryUpdateRequest request
    ) {
        return MemoryOut.fromEntity(memoryService.updateMemory(
            userId,
            memoryId,
            request.getCategory(),
            request.getKey(),
            request.getValue(),
            request.getConfidence(),
            request.getSource(),
            request.getPinned(),
            request.getStatus(),
            request.getExpiresAt(),
            request.getClearExpiry()
        ));
    }

    @DeleteMapping("/users/{userId}/memories/{memoryId}")
    public Map<String, Boolean> deleteMemory(@PathVariable String userId, @PathVariable Long memoryId) {
        boolean deleted = memoryService.deleteMemory(userId, memoryId);
        if (!deleted) {
            throw new IllegalArgumentException("Memory not found");
        }
        return Map.of("deleted", true);
    }

    @GetMapping("/users/{userId}/memories/review")
    public MemoryReviewOut reviewMemories(@PathVariable String userId) {
        return memoryService.reviewMemories(userId);
    }

    @GetMapping("/users/{userId}/conversations")
    public List<ConversationOut> listConversations(@PathVariable String userId) {
        return memoryService.recentTurns(userId).stream().map(ConversationOut::fromEntity).toList();
    }
}
