package com.wc.aiservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wc.aiservice.model.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@EnableCaching
public class TwoLevelCacheService {

    private final Cache<String, ChatResponse> localCache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager caffeineCacheManager;
    private final ObjectMapper objectMapper;

    private static final String REDIS_KEY_PREFIX = "ai_response:";
    private static final long LOCAL_CACHE_TTL_SECONDS = 60;
    private static final long REDIS_CACHE_TTL_SECONDS = 3600;

    public TwoLevelCacheService(RedisTemplate<String, Object> redisTemplate,
                                CacheManager caffeineCacheManager,
                                ObjectMapper objectMapper) {
        this.localCache = Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(500)
                .expireAfterWrite(LOCAL_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
                .recordStats()
                .build();
        
        this.redisTemplate = redisTemplate;
        this.caffeineCacheManager = caffeineCacheManager;
        this.objectMapper = objectMapper;
    }

    public ChatResponse get(String key) {
        // L1: 本地Caffeine缓存
        ChatResponse response = localCache.getIfPresent(key);
        if (response != null) {
            log.debug("L1缓存命中: {}", key);
            // 标记为缓存命中
            response.setCached(true);
            return response;
        }

        // L2: Redis缓存
        response = getFromRedis(key);
        if (response != null) {
            localCache.put(key, response);
            log.debug("L2缓存命中，L1回填: {}", key);
            // 标记为缓存命中
            response.setCached(true);
            return response;
        }

        log.debug("缓存未命中: {}", key);
        return null;
    }

    public void put(String key, ChatResponse value) {
        // 写入L1本地缓存
        localCache.put(key, value);
        
        // 写入L2 Redis缓存 (使用JSON序列化)
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + key,
                    json,
                    REDIS_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            log.debug("写入二级缓存: key={}, cached={}", key, value.isCached());
        } catch (Exception e) {
            log.error("写入Redis缓存失败: {}", e.getMessage());
        }
    }

    public void evict(String key) {
        localCache.invalidate(key);
        redisTemplate.delete(REDIS_KEY_PREFIX + key);
        log.debug("清除缓存: {}", key);
    }

    private ChatResponse getFromRedis(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + key);
            if (value == null) {
                return null;
            }
            
            // 如果是String类型（JSON），反序列化为ChatResponse
            if (value instanceof String) {
                return objectMapper.readValue((String) value, ChatResponse.class);
            }
            
            // 如果已经是ChatResponse类型（直接反序列化）
            if (value instanceof ChatResponse) {
                return (ChatResponse) value;
            }
        } catch (Exception e) {
            log.error("Redis获取失败: {}", e.getMessage());
        }
        return null;
    }

    public void clearLocalCache() {
        localCache.invalidateAll();
        log.info("L1本地缓存已清空");
    }

    public String getCacheStats() {
        return localCache.stats().toString();
    }
}
