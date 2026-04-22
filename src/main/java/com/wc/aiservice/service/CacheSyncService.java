package com.wc.aiservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class CacheSyncService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String CACHE_SYNC_TOPIC = "cache:sync";
    private static final String CACHE_INVALIDATION_QUEUE = "cache:invalidation:queue";

    public CacheSyncService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publishCacheUpdate(String cacheKey, String action, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("cacheKey", cacheKey);
        message.put("action", action);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());
        message.put("source", "ai-service");

        redisTemplate.convertAndSend(CACHE_SYNC_TOPIC, message);
        log.info("发布缓存更新消息: key={}, action={}", cacheKey, action);
    }

    public void publishCacheInvalidation(String cacheKey) {
        Map<String, Object> message = new HashMap<>();
        message.put("cacheKey", cacheKey);
        message.put("action", "INVALIDATE");
        message.put("timestamp", System.currentTimeMillis());
        message.put("source", "ai-service");

        redisTemplate.convertAndSend(CACHE_INVALIDATION_QUEUE, message);
        log.info("发布缓存失效消息: {}", cacheKey);
    }

    public void syncUserSession(String userId, Map<String, Object> sessionData) {
        String key = "user:session:sync:" + userId;
        redisTemplate.opsForHash().putAll(key, sessionData);
        redisTemplate.expire(key, Duration.ofSeconds(3600));
        
        publishCacheUpdate(key, "SYNC_SESSION", sessionData);
    }

    public void broadcastCacheClear(String pattern) {
        Map<String, Object> message = new HashMap<>();
        message.put("action", "CLEAR_PATTERN");
        message.put("pattern", pattern);
        message.put("timestamp", System.currentTimeMillis());

        redisTemplate.convertAndSend(CACHE_SYNC_TOPIC, message);
        log.info("广播缓存清除: pattern={}", pattern);
    }
}
