package com.wc.aiservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class IpRateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:ip:";
    private static final String BLACKLIST_KEY = "ip:blacklist";
    private static final String WHITELIST_KEY = "ip:whitelist";
    
    private final Set<String> localBlacklist = ConcurrentHashMap.newKeySet();
    private final Set<String> localWhitelist = ConcurrentHashMap.newKeySet();
    
    private static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 100;
    private static final int DEFAULT_WINDOW_SECONDS = 60;

    public IpRateLimitService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        loadIpLists();
    }

    private void loadIpLists() {
        localBlacklist.add("192.168.1.100");
        localBlacklist.add("10.0.0.50");
        
        localWhitelist.add("127.0.0.1");
        localWhitelist.add("192.168.1.1");
        
        log.info("IP黑白名单加载完成: 黑名单{}个, 白名单{}个", 
                localBlacklist.size(), localWhitelist.size());
    }

    public boolean isAllowed(String clientIp, int maxRequestsPerMinute) {
        if (localWhitelist.contains(clientIp)) {
            return true;
        }
        
        if (localBlacklist.contains(clientIp)) {
            log.warn("IP在黑名单中，拒绝访问: {}", clientIp);
            return false;
        }

        String key = RATE_LIMIT_KEY_PREFIX + clientIp;
        Long currentCount = redisTemplate.opsForValue().increment(key, 1);
        
        if (currentCount != null && currentCount == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(DEFAULT_WINDOW_SECONDS));
        }

        if (currentCount != null && currentCount > maxRequestsPerMinute) {
            log.warn("IP限流触发: ip={}, 当前请求数={}", clientIp, currentCount);
            return false;
        }

        return true;
    }

    public boolean isAllowed(String clientIp) {
        return isAllowed(clientIp, DEFAULT_MAX_REQUESTS_PER_MINUTE);
    }

    public void addToBlacklist(String ip) {
        localBlacklist.add(ip);
        redisTemplate.opsForSet().add(BLACKLIST_KEY, ip);
        log.info("IP加入黑名单: {}", ip);
    }

    public void removeFromBlacklist(String ip) {
        localBlacklist.remove(ip);
        redisTemplate.opsForSet().remove(BLACKLIST_KEY, ip);
        log.info("IP移出黑名单: {}", ip);
    }

    public void addToWhitelist(String ip) {
        localWhitelist.add(ip);
        redisTemplate.opsForSet().add(WHITELIST_KEY, ip);
        log.info("IP加入白名单: {}", ip);
    }

    public void removeFromWhitelist(String ip) {
        localWhitelist.remove(ip);
        redisTemplate.opsForSet().remove(WHITELIST_KEY, ip);
        log.info("IP移出白名单: {}", ip);
    }

    public boolean isInBlacklist(String ip) {
        return localBlacklist.contains(ip);
    }

    public boolean isInWhitelist(String ip) {
        return localWhitelist.contains(ip);
    }

    public void clearRateLimit(String ip) {
        String key = RATE_LIMIT_KEY_PREFIX + ip;
        redisTemplate.delete(key);
    }

    public long getCurrentRequestCount(String ip) {
        String key = RATE_LIMIT_KEY_PREFIX + ip;
        Object count = redisTemplate.opsForValue().get(key);
        return count != null ? ((Number) count).longValue() : 0;
    }
}
