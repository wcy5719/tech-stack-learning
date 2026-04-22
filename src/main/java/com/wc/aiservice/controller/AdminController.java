package com.wc.aiservice.controller;

import com.wc.aiservice.service.AiCustomerService;
import com.wc.aiservice.service.BloomFilterService;
import com.wc.aiservice.service.IpRateLimitService;
import com.wc.aiservice.service.TwoLevelCacheService;
import com.wc.aiservice.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TwoLevelCacheService twoLevelCacheService;
    private final BloomFilterService bloomFilterService;
    private final IpRateLimitService ipRateLimitService;
    private final ChatWebSocketHandler chatWebSocketHandler;
    private final AiCustomerService aiCustomerService;

    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        return ResponseEntity.ok(Map.of(
                "localCacheStats", twoLevelCacheService.getCacheStats(),
                "bloomFilterStatus", "active"
        ));
    }

    @PostMapping("/cache/clear")
    public ResponseEntity<String> clearCache() {
        twoLevelCacheService.clearLocalCache();
        bloomFilterService.clear();
        return ResponseEntity.ok("缓存已清空");
    }

    @GetMapping("/ip/blacklist")
    public ResponseEntity<String> checkBlacklist(@RequestParam String ip) {
        boolean inBlacklist = ipRateLimitService.isInBlacklist(ip);
        return ResponseEntity.ok("IP " + ip + (inBlacklist ? " 在黑名单中" : " 不在黑名单中"));
    }

    @PostMapping("/ip/blacklist/add")
    public ResponseEntity<String> addToBlacklist(@RequestParam String ip) {
        ipRateLimitService.addToBlacklist(ip);
        return ResponseEntity.ok("IP " + ip + " 已加入黑名单");
    }

    @DeleteMapping("/ip/blacklist/remove")
    public ResponseEntity<String> removeFromBlacklist(@RequestParam String ip) {
        ipRateLimitService.removeFromBlacklist(ip);
        return ResponseEntity.ok("IP " + ip + " 已从黑名单移除");
    }

    @GetMapping("/websocket/sessions")
    public ResponseEntity<Integer> getWebSocketSessionCount() {
        return ResponseEntity.ok(chatWebSocketHandler.getActiveSessionCount());
    }
}
