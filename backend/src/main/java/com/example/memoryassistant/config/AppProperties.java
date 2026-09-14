package com.example.memoryassistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String appName = "memory-assistant-java";
    private String ollamaBaseUrl = "http://127.0.0.1:11434";
    private String deepseekBaseUrl = "https://api.deepseek.com";
    private String deepseekApiKey = "";
    private String deepseekModel = "deepseek-flash";
    private List<String> allowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");
    private int recentTurnsLimit = 6;
    private int memoryLimit = 5;
    private boolean memoryDecayEnabled = true;
    private int memoryDecayIntervalDays = 30;
    private double memoryDecayFactor = 0.08;
    private double lowConfidenceThreshold = 0.35;
    private int expiringSoonDays = 7;
    private int ongoingTaskTtlDays = 45;
}
