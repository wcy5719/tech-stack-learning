package com.wc.aiservice.repository;

import com.wc.aiservice.model.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findByUserIdOrderByCreatedAtDesc(String userId);

    List<ChatHistory> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<ChatHistory> findByUserIdAndCreatedAtBetween(String userId, LocalDateTime start, LocalDateTime end);

    List<ChatHistory> findByIntent(String intent);
}
