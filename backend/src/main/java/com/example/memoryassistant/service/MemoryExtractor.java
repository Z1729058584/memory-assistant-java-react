package com.example.memoryassistant.service;

import com.example.memoryassistant.model.MemoryCandidate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MemoryExtractor {

    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    public MemoryExtractor(DeepSeekClient deepSeekClient, ObjectMapper objectMapper) {
        this.deepSeekClient = deepSeekClient;
        this.objectMapper = objectMapper;
    }

    public List<MemoryCandidate> extract(String message, String modelId) {
        // 双通道抽取：规则保证稳定，LLM 提升覆盖率。
        List<MemoryCandidate> all = new ArrayList<>();
        all.addAll(ruleExtract(message));
        all.addAll(llmExtract(message, modelId));
        return dedupe(all);
    }

    private List<MemoryCandidate> ruleExtract(String message) {
        // 规则抽取覆盖高确定性的偏好/技术栈线索。
        String lower = message.toLowerCase(Locale.ROOT);
        List<MemoryCandidate> results = new ArrayList<>();

        if (lower.contains("react")) {
            results.add(new MemoryCandidate("stack", "react", "React", 0.9, "rule", false, null));
        }
        if (lower.contains("java")) {
            results.add(new MemoryCandidate("stack", "java", "Java", 0.9, "rule", false, null));
        }
        if (lower.contains("ollama")) {
            results.add(new MemoryCandidate("stack", "ollama", "Ollama", 0.9, "rule", false, null));
        }
        if (lower.contains("deepseek")) {
            results.add(new MemoryCandidate("stack", "deepseek", "DeepSeek", 0.9, "rule", false, null));
        }

        if (lower.contains("reply in english") || lower.contains("speak english")) {
            results.add(new MemoryCandidate("preference", "language", "en-US", 0.98, "rule", false, null));
        }
        if (message.contains("中文") || message.contains("汉语")) {
            results.add(new MemoryCandidate("preference", "language", "zh-CN", 0.98, "rule", false, null));
        }

        Matcher taskMatcher = Pattern.compile("(正在做|在做|working on)\\s*[:：]?\\s*(.+)", Pattern.CASE_INSENSITIVE)
            .matcher(message);
        if (taskMatcher.find()) {
            String task = taskMatcher.group(2).trim();
            if (!task.isBlank()) {
                results.add(
                    new MemoryCandidate(
                        "ongoing_task",
                        "current_project",
                        task,
                        0.82,
                        "rule",
                        false,
                        LocalDateTime.now().plusDays(45)
                    )
                );
            }
        }

        return results;
    }

    private List<MemoryCandidate> llmExtract(String message, String modelId) {
        if (!deepSeekClient.isAvailable()) {
            // 模型不可用时直接返回空列表，不影响聊天主链路。
            return List.of();
        }

        try {
            String prompt = "Extract only durable user memory from the message. Return a JSON array. " +
                "Each item must include category, key, value, confidence, source. " +
                "Ignore temporary facts and avoid duplication.";

            List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", prompt),
                Map.of("role", "user", "content", message)
            );
            String content = deepSeekClient.chat(modelId, messages);
            String jsonArray = extractJsonArray(content);
            if (jsonArray == null) {
                return List.of();
            }

            JsonNode node = objectMapper.readTree(jsonArray);
            if (!node.isArray()) {
                return List.of();
            }

            List<MemoryCandidate> extracted = new ArrayList<>();
            for (JsonNode item : node) {
                String category = item.path("category").asText("").trim();
                String key = item.path("key").asText("").trim();
                String value = item.path("value").asText("").trim();
                if (category.isBlank() || key.isBlank() || value.isBlank()) {
                    continue;
                }
                // 模型可能输出越界置信度，这里统一钳制到 0~1。
                double confidence = clamp(item.path("confidence").asDouble(0.8), 0.0, 1.0);
                String source = item.path("source").asText("deepseek");
                extracted.add(new MemoryCandidate(category, key, value, confidence, source, false, null));
            }
            return extracted;
        } catch (Exception ignored) {
            // 抽取失败只影响增强能力，不应中断主请求。
            return List.of();
        }
    }

    private String extractJsonArray(String text) {
        // 容错提取：允许模型在 JSON 数组前后输出解释性文本。
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private List<MemoryCandidate> dedupe(List<MemoryCandidate> input) {
        Map<String, MemoryCandidate> seen = new LinkedHashMap<>();
        for (MemoryCandidate item : input) {
            String key = (item.category() + "|" + item.key() + "|" + normalize(item.value())).toLowerCase(Locale.ROOT);
            MemoryCandidate current = seen.get(key);
            // 重复候选保留更高置信度版本。
            if (current == null || item.confidence() > current.confidence()) {
                seen.put(key, item);
            }
        }
        return new ArrayList<>(seen.values());
    }

    private String normalize(String input) {
        return input.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
