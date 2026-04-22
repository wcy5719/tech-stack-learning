package com.wc.aiservice.service;

import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

@Slf4j
@Service
public class BloomFilterService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String BLOOM_FILTER_KEY = "bloom_filter:requests";
    private static final int EXPECTED_INSERTIONS = 100000;
    private static final double FALSE_POSITIVE_RATE = 0.01;

    public BloomFilterService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean mightContain(String value) {
        int[] positions = getHashPositions(value);
        for (int position : positions) {
            String key = BLOOM_FILTER_KEY + ":" + position;
            Boolean exists = redisTemplate.hasKey(key);
            if (Boolean.FALSE.equals(exists)) {
                return false;
            }
        }
        return true;
    }

    public void put(String value) {
        int[] positions = getHashPositions(value);
        for (int position : positions) {
            String key = BLOOM_FILTER_KEY + ":" + position;
            redisTemplate.opsForValue().set(key, "1");
        }
        log.debug("布隆过滤器添加: {}", value);
    }

    private int[] getHashPositions(String value) {
        int numHashFunctions = getNumHashFunctions();
        int[] positions = new int[numHashFunctions];
        
        long hash1 = hash1(value);
        long hash2 = hash2(value);
        
        for (int i = 0; i < numHashFunctions; i++) {
            positions[i] = (int) ((hash1 + i * hash2) % (EXPECTED_INSERTIONS * 10));
        }
        
        return positions;
    }

    private long hash1(String value) {
        CRC32 crc32 = new CRC32();
        crc32.update(value.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }

    private long hash2(String value) {
        return (value.hashCode() & 0xFFFFFFFFL);
    }

    private int getNumHashFunctions() {
        return (int) Math.ceil(Math.log(1.0 / FALSE_POSITIVE_RATE) / Math.log(2));
    }

    public void clear() {
        for (int i = 0; i < EXPECTED_INSERTIONS * 10; i++) {
            redisTemplate.delete(BLOOM_FILTER_KEY + ":" + i);
        }
        log.info("布隆过滤器已清空");
    }
}
