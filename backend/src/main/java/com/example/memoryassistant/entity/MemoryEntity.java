package com.example.memoryassistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "memories")
public class MemoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "\"key\"", nullable = false, length = 128)
    private String memoryKey;

    @Column(name = "value", nullable = false, length = 4000)
    private String value;

    @Column(name = "normalized_value", length = 255)
    private String normalizedValue;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "source", length = 255)
    private String source;

    @Column(name = "pinned")
    private Boolean pinned;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "access_count")
    private Integer accessCount;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (confidence == null) {
            confidence = 1.0;
        }
        if (source == null || source.isBlank()) {
            source = "chat";
        }
        if (pinned == null) {
            pinned = false;
        }
        if (status == null || status.isBlank()) {
            status = "active";
        }
        if (accessCount == null) {
            accessCount = 0;
        }
        if (normalizedValue == null || normalizedValue.isBlank()) {
            normalizedValue = normalize(value);
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (normalizedValue == null || normalizedValue.isBlank()) {
            normalizedValue = normalize(value);
        }
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
