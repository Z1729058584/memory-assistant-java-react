package com.example.memoryassistant.service;

import com.example.memoryassistant.config.AppProperties;
import com.example.memoryassistant.entity.ConversationTurnEntity;
import com.example.memoryassistant.entity.MemoryEntity;
import com.example.memoryassistant.model.AppConfigOut;
import com.example.memoryassistant.model.ChatRequest;
import com.example.memoryassistant.model.ChatResponse;
import com.example.memoryassistant.model.MemoryCandidate;
import com.example.memoryassistant.model.MemoryOut;
import com.example.memoryassistant.model.ModelOption;
import com.example.memoryassistant.model.ProviderOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {

    private final MemoryService memoryService;
    private final MemoryExtractor memoryExtractor;
    private final DeepSeekClient deepSeekClient;
    private final AppProperties appProperties;

    public AssistantService(MemoryService memoryService, MemoryExtractor memoryExtractor, DeepSeekClient deepSeekClient, AppProperties appProperties) {
        this.memoryService = memoryService;
        this.memoryExtractor = memoryExtractor;
        this.deepSeekClient = deepSeekClient;
        this.appProperties = appProperties;
    }

    public AppConfigOut appConfig(String requestedModel) {
        // 前端模型下拉框依赖这里返回的云端模型列表。
        List<ModelOption> models = deepSeekClient.listModels();
        String modelId = resolveModelId(requestedModel, models);
        ProviderOption provider = new ProviderOption("deepseek", "DeepSeek API", appProperties.getDeepseekBaseUrl(), deepSeekClient.isAvailable(), models);
        return new AppConfigOut(provider.available(), "deepseek", modelId, "cloud", List.of(provider));
    }

    public ChatResponse chat(ChatRequest request, String requestedModel) {
        String userId = request.getUserId();
        String message = request.getMessage();
        if (userId == null || userId.isBlank() || message == null || message.isBlank()) {
            throw new IllegalArgumentException("userId and message are required.");
        }

        List<ModelOption> models = deepSeekClient.listModels();
        String modelId = resolveModelId(requestedModel, models);
        // 检索会结合关键词匹配和记忆衰减后的置信度打分。
        List<MemoryEntity> used = memoryService.retrieveRelevantMemories(userId, message);
        // 最近对话用于构造短上下文窗口，避免每次都从零开始。
        List<ConversationTurnEntity> recent = memoryService.recentTurns(userId);

        String reply;
        String mode;
        String error = null;
        try {
            reply = generateAiReply(message, used, recent, modelId);
            mode = "ai";
        } catch (Exception ex) {
            // 即使 DeepSeek 不可用，接口也降级返回，保证功能可用。
            mode = "fallback";
            error = ex.getMessage();
            reply = fallbackReply(modelId, used, recent, error);
        }

        // 无论 AI 还是降级模式，都会落对话并尝试更新记忆。
        List<MemoryCandidate> extracted = memoryExtractor.extract(message, modelId);
        memoryService.addTurn(userId, "user", message);
        memoryService.addTurn(userId, "assistant", reply);
        memoryService.upsertMemories(userId, extracted);

        return new ChatResponse(
            reply,
            used.stream().map(MemoryOut::fromEntity).toList(),
            extracted,
            mode,
            "deepseek",
            "ai".equals(mode) ? modelId : null,
            error
        );
    }

    private String generateAiReply(String message, List<MemoryEntity> used, List<ConversationTurnEntity> recent, String modelId) {
        List<String> memoryLines = used.stream()
            .map(m -> "- " + m.getCategory() + "." + m.getMemoryKey() + ": " + m.getValue())
            .toList();

        // 记忆以 system 提示注入，减少把原始数据库字段直接暴露给模型的噪声。
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "You are a conversational AI assistant inside a memory console. Answer naturally and use stored memory only when relevant."));
        messages.add(Map.of("role", "system", "content", "Relevant memory:\n" + (memoryLines.isEmpty() ? "- none" : String.join("\n", memoryLines))));

        Set<String> allowed = Set.of("user", "assistant");
        for (ConversationTurnEntity t : recent) {
            if (allowed.contains(t.getRole())) {
                messages.add(Map.of("role", t.getRole(), "content", t.getContent()));
            }
        }
        // 当前用户消息必须作为最后一条，确保模型针对本轮输入作答。
        messages.add(Map.of("role", "user", "content", message));
        return deepSeekClient.chat(modelId, messages);
    }

    private String fallbackReply(String modelId, List<MemoryEntity> used, List<ConversationTurnEntity> recent, String error) {
        String memoryHint = used.stream().limit(4).map(m -> m.getMemoryKey() + "=" + m.getValue()).reduce((a, b) -> a + "; " + b).orElse("none");
        return "The app is running in fallback mode. Selected model: deepseek/" + modelId +
            ". Matched memory: " + memoryHint +
            ". Recent turn count: " + recent.size() +
            ". Last model error: " + error +
            " Configure DEEPSEEK_API_KEY, then restart the backend.";
    }

    private String resolveModelId(String requestedModel, List<ModelOption> models) {
        // API key 未配置时仍返回默认模型 id，避免配置接口直接不可用。
        if (models.isEmpty()) return deepSeekClient.defaultModel();
        if (requestedModel != null && !requestedModel.isBlank()) {
            for (ModelOption m : models) {
                if (m.id().equals(requestedModel)) return requestedModel;
            }
        }
        for (ModelOption m : models) {
            if (m.id().equals(deepSeekClient.defaultModel())) return deepSeekClient.defaultModel();
        }
        return models.getFirst().id();
    }
}
