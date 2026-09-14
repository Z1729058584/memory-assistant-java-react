package com.example.memoryassistant;

import com.example.memoryassistant.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class MemoryAssistantBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemoryAssistantBackendApplication.class, args);
    }
}
