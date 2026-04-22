package com.wc.aiservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final long DEFAULT_EXPIRE_SECONDS = 86400;

    public boolean checkAndSetIdempotencyKey(String idempotencyKey) {
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "1", DEFAULT_EXPIRE_SECONDS, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(success)) {
            log.info("幂等校验通过: {}", idempotencyKey);
            return true;
        }
        log.warn("重复请求检测到: {}", idempotencyKey);
        return false;
    }

    public boolean isDuplicateRequest(String idempotencyKey) {
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void removeIdempotencyKey(String idempotencyKey) {
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        redisTemplate.delete(key);
    }

    public String generateRequestHash(String userId, String sessionId, String message) {
        return String.valueOf((userId + sessionId + message).hashCode());
    }
}
