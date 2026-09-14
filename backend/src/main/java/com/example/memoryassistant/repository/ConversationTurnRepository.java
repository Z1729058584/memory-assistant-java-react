package com.example.memoryassistant.repository;

import com.example.memoryassistant.entity.ConversationTurnEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationTurnRepository extends JpaRepository<ConversationTurnEntity, Long> {
    List<ConversationTurnEntity> findByUserId(String userId, Pageable pageable);
}
