package com.example.memoryassistant.service;

import com.example.memoryassistant.config.AppProperties;
import com.example.memoryassistant.model.ModelOption;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OllamaClient {

    // 当 Ollama 不可达时使用默认模型列表，保证前端配置页可展示可选项。
    private static final List<ModelOption> DEFAULT_MODELS = List.of(
        new ModelOption("qwen2.5:7b-instruct", "Qwen 2.5 7B Instruct", true, "Recommended local model."),
        new ModelOption("llama3.2:3b", "Llama 3.2 3B", true, "Small local model."),
        new ModelOption("deepseek-r1:7b", "DeepSeek R1 7B", true, "Reasoning-oriented local model.")
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public OllamaClient(ObjectMapper objectMapper, AppProperties appProperties) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public boolean isAvailable() {
        try {
            // 通过 /api/tags 做轻量探活，超时较短，避免拖慢主流程。
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/tags"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    public List<ModelOption> listModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/tags"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return DEFAULT_MODELS;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode modelsNode = root.path("models");
            if (!modelsNode.isArray() || modelsNode.isEmpty()) {
                return DEFAULT_MODELS;
            }

            List<ModelOption> models = new ArrayList<>();
            for (JsonNode node : modelsNode) {
                String name = node.path("name").asText("");
                if (!name.isBlank()) {
                    // 这里直接使用模型名作为 id，前后端统一按 name 传递。
                    models.add(new ModelOption(name, name, true, "Installed local Ollama model."));
                }
            }
            return models.isEmpty() ? DEFAULT_MODELS : models;
        } catch (Exception ignored) {
            return DEFAULT_MODELS;
        }
    }

    public String chat(String model, List<Map<String, String>> messages) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model);
            payload.put("messages", messages);
            // 当前后端走非流式，便于接口一次性返回完整 reply。
            payload.put("stream", false);

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/chat"))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Ollama returned HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            // Ollama chat 返回结构：{ message: { content: "..." } }。
            String content = root.path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new RuntimeException("Ollama returned empty response.");
            }
            return content;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Ollama response.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Ollama request was interrupted.", e);
        } catch (Exception e) {
            throw new RuntimeException("Cannot call Ollama at " + baseUrl() + ": " + e.getMessage(), e);
        }
    }

    public String baseUrl() {
        // 统一去掉末尾斜杠，避免拼接路径时出现双斜杠。
        return appProperties.getOllamaBaseUrl().replaceAll("/$", "");
    }
}
