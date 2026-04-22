package com.wc.aiservice.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SensitiveWordFilterService {

    private final Set<String> sensitiveWords = ConcurrentHashMap.newKeySet();
    private final Set<Pattern> sensitivePatterns = ConcurrentHashMap.newKeySet();
    
    private static final String MASK_CHAR = "*";
    
    @PostConstruct
    public void init() {
        loadSensitiveWords();
    }

    private void loadSensitiveWords() {
        sensitiveWords.add("政治敏感");
        sensitiveWords.add("暴力内容");
        sensitiveWords.add("色情低俗");
        sensitiveWords.add("赌博");
        sensitiveWords.add("毒品");
        sensitiveWords.add("诈骗");
        sensitiveWords.add("骂人");
        sensitiveWords.add("脏话");
        
        sensitivePatterns.add(Pattern.compile("\\d{11}"));
        sensitivePatterns.add(Pattern.compile("\\d{3,4}-\\d{7,8}"));
        
        log.info("敏感词库加载完成，共 {} 个敏感词", sensitiveWords.size());
    }

    public FilterResult filter(String content) {
        if (content == null || content.isEmpty()) {
            return new FilterResult(true, content, false);
        }

        String masked = content;
        boolean hasSensitive = false;

        for (String word : sensitiveWords) {
            if (content.contains(word)) {
                hasSensitive = true;
                masked = masked.replace(word, repeatMask(word.length()));
            }
        }

        for (Pattern pattern : sensitivePatterns) {
            if (pattern.matcher(content).find()) {
                hasSensitive = true;
                masked = pattern.matcher(masked).replaceAll(m -> maskPhone(m.group()));
            }
        }

        return new FilterResult(!hasSensitive, masked, hasSensitive);
    }

    public boolean containsSensitiveWord(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        String lowerContent = content.toLowerCase();
        return sensitiveWords.stream()
                .anyMatch(word -> lowerContent.contains(word.toLowerCase()));
    }

    public void addSensitiveWord(String word) {
        sensitiveWords.add(word);
        log.info("添加敏感词: {}", word);
    }

    public void removeSensitiveWord(String word) {
        sensitiveWords.remove(word);
        log.info("移除敏感词: {}", word);
    }

    private String repeatMask(int length) {
        return MASK_CHAR.repeat(Math.max(1, length));
    }

    private String maskPhone(String phone) {
        if (phone.length() == 11) {
            return phone.substring(0, 3) + "****" + phone.substring(7);
        }
        return repeatMask(phone.length());
    }

    public record FilterResult(boolean passed, String filteredContent, boolean hasSensitive) {}
}
