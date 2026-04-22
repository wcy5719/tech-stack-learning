package com.wc.aiservice.repository;

import com.wc.aiservice.model.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    List<ChatSession> findByUserIdOrderByLastMessageTimeDesc(String userId);

    Optional<ChatSession> findBySessionIdAndUserId(String sessionId, String userId);

    List<ChatSession> findByStatusAndLastMessageTimeBefore(String status, LocalDateTime time);

    long countByUserId(String userId);
}
