package com.wc.aiservice.controller;

import com.wc.aiservice.model.ChatRequest;
import com.wc.aiservice.model.ChatResponse;
import com.wc.aiservice.service.AsyncChatService;
import com.wc.aiservice.service.AiCustomerService;
import com.wc.aiservice.service.VllmService;
import com.wc.aiservice.service.MultiTurnConversationService;
import com.wc.aiservice.service.TwoLevelCacheService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AiCustomerServiceController {

    private final AiCustomerService aiCustomerService;
    private final VllmService vllmService;
    private final MultiTurnConversationService multiTurnConversationService;
    private final TwoLevelCacheService twoLevelCacheService;
    private final AsyncChatService asyncChatService;

    @PostMapping("/chat")
    public DeferredResult<ResponseEntity<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        DeferredResult<ResponseEntity<ChatResponse>> deferredResult = new DeferredResult<>(600_000L);
        
        deferredResult.onTimeout(() -> {
            ChatResponse timeoutResponse = ChatResponse.builder()
                    .response("请求处理超时，请稍后再试")
                    .userId(request.getUserId())
                    .sessionId(request.getSessionId())
                    .timestamp(System.currentTimeMillis())
                    .build();
            deferredResult.setResult(ResponseEntity.ok(timeoutResponse));
        });
        
        CompletableFuture<ChatResponse> future = asyncChatService.processChatAsync(request);
        
        future.whenComplete((response, error) -> {
            if (error != null) {
                ChatResponse errorResponse = ChatResponse.builder()
                        .response("抱歉，处理您的请求时出现错误: " + error.getMessage())
                        .userId(request.getUserId())
                        .sessionId(request.getSessionId())
                        .timestamp(System.currentTimeMillis())
                        .build();
                deferredResult.setResult(ResponseEntity.status(500).body(errorResponse));
            } else {
                deferredResult.setResult(ResponseEntity.ok(response));
            }
        });
        
        return deferredResult;
    }

    @PostMapping("/chat/async")
    public ResponseEntity<Void> asyncChat(@Valid @RequestBody ChatRequest request) {
        asyncChatService.processChatAsync(request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/conversation/{sessionId}/history")
    public ResponseEntity<List<MultiTurnConversationService.ChatMessage>> getHistory(
            @PathVariable String sessionId) {
        return ResponseEntity.ok(multiTurnConversationService.getConversationHistory(sessionId));
    }

    @DeleteMapping("/conversation/{sessionId}")
    public ResponseEntity<Void> clearHistory(@PathVariable String sessionId) {
        multiTurnConversationService.clearConversation(sessionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/cache/stats")
    public ResponseEntity<String> getCacheStats() {
        return ResponseEntity.ok(twoLevelCacheService.getCacheStats());
    }

    @DeleteMapping("/cache/clear")
    public ResponseEntity<Void> clearCache() {
        twoLevelCacheService.clearLocalCache();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean vllmHealthy = vllmService.isHealthy();
        return ResponseEntity.ok(Map.of(
                "status", vllmHealthy ? "UP" : "DEGRADED",
                "vllm", vllmHealthy ? "UP" : "DOWN",
                "openai", "UP"
        ));
    }
}
