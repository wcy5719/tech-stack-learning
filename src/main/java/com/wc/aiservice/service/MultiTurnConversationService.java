package com.wc.aiservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiTurnConversationService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String CONVERSATION_KEY_PREFIX = "conversation:";
    private static final int MAX_HISTORY_SIZE = 20;
    private static final long SESSION_EXPIRE_SECONDS = 3600;

    public void addMessage(String sessionId, String role, String content) {
        String key = CONVERSATION_KEY_PREFIX + sessionId;
        
        List<ChatMessage> history = getConversationHistory(sessionId);
        
        ChatMessage message = new ChatMessage(role, content, System.currentTimeMillis());
        history.add(message);
        
        if (history.size() > MAX_HISTORY_SIZE) {
            history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY_SIZE, history.size()));
        }
        
        redisTemplate.opsForValue().set(key, history, SESSION_EXPIRE_SECONDS, TimeUnit.SECONDS);
        log.debug("会话 {} 添加消息，当前历史 {} 条", sessionId, history.size());
    }

    @SuppressWarnings("unchecked")
    public List<ChatMessage> getConversationHistory(String sessionId) {
        String key = CONVERSATION_KEY_PREFIX + sessionId;
        Object cached = redisTemplate.opsForValue().get(key);
        
        if (cached instanceof List) {
            return new ArrayList<>((List<ChatMessage>) cached);
        }
        return new ArrayList<>();
    }

    public String buildContextWithHistory(String sessionId, String currentMessage) {
        List<ChatMessage> history = getConversationHistory(sessionId);
        
        StringBuilder context = new StringBuilder();
        context.append("【对话历史】\n");
        
        for (ChatMessage msg : history) {
            context.append(msg.role()).append(": ").append(msg.content()).append("\n");
        }
        
        context.append("【当前消息】\n");
        context.append("user: ").append(currentMessage);
        
        return context.toString();
    }

    public void clearConversation(String sessionId) {
        String key = CONVERSATION_KEY_PREFIX + sessionId;
        redisTemplate.delete(key);
        log.info("清除会话历史: {}", sessionId);
    }

    public void extendSession(String sessionId) {
        String key = CONVERSATION_KEY_PREFIX + sessionId;
        redisTemplate.expire(key, SESSION_EXPIRE_SECONDS, TimeUnit.SECONDS);
    }

    public record ChatMessage(String role, String content, long timestamp) {}
}
