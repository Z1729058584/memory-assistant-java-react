package com.example.memoryassistant.repository;

import com.example.memoryassistant.entity.MemoryEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryRepository extends JpaRepository<MemoryEntity, Long> {
    List<MemoryEntity> findByUserId(String userId);

    List<MemoryEntity> findByUserIdAndStatus(String userId, String status);

    Optional<MemoryEntity> findFirstByUserIdAndCategoryAndMemoryKey(String userId, String category, String memoryKey);

    Optional<MemoryEntity> findFirstByUserIdAndCategoryAndNormalizedValueAndStatus(
        String userId,
        String category,
        String normalizedValue,
        String status
    );

    Optional<MemoryEntity> findByIdAndUserId(Long id, String userId);
}
