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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DeepSeekClient {

    private static final List<ModelOption> MODELS = List.of(
        new ModelOption("deepseek-flash", "DeepSeek Flash", false, "Fast DeepSeek model for daily chat and extraction."),
        new ModelOption("deepseek-v4-pro", "DeepSeek V4 Pro", false, "Stronger DeepSeek model for complex reasoning.")
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public DeepSeekClient(ObjectMapper objectMapper, AppProperties appProperties) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public boolean isAvailable() {
        return !apiKey().isBlank();
    }

    public List<ModelOption> listModels() {
        return MODELS;
    }

    public String defaultModel() {
        String configured = appProperties.getDeepseekModel();
        return configured == null || configured.isBlank() ? "deepseek-flash" : configured.trim();
    }

    public String chat(String model, List<Map<String, String>> messages) {
        if (apiKey().isBlank()) {
            throw new RuntimeException("DeepSeek API key is not configured. Set DEEPSEEK_API_KEY before starting the backend.");
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model);
            payload.put("messages", messages);
            payload.put("stream", false);
            payload.put("thinking", Map.of("type", "disabled"));

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("DeepSeek returned HTTP " + response.statusCode() + ": " + clip(response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new RuntimeException("DeepSeek returned empty response.");
            }
            return content;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse DeepSeek response.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("DeepSeek request was interrupted.", e);
        } catch (Exception e) {
            throw new RuntimeException("Cannot call DeepSeek at " + baseUrl() + ": " + e.getMessage(), e);
        }
    }

    public String baseUrl() {
        return appProperties.getDeepseekBaseUrl().replaceAll("/$", "");
    }

    private String apiKey() {
        String configured = firstNotBlank(
            appProperties.getDeepseekApiKey(),
            System.getProperty("app.deepseek-api-key"),
            System.getProperty("DEEPSEEK_API_KEY"),
            System.getenv("DEEPSEEK_API_KEY")
        );
        return configured == null ? "" : configured.trim();
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String clip(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
