package com.wc.aiservice.service;

import com.wc.aiservice.model.ChatRequest;
import com.wc.aiservice.model.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncChatService {

    private final AiCustomerService aiCustomerService;

    /**
     * 异步处理聊天请求
     * 适用于高并发场景，不阻塞 Tomcat 线程
     */
    @Async("chatTaskExecutor")
    public CompletableFuture<ChatResponse> processChatAsync(ChatRequest request) {
        log.info("异步处理聊天请求: userId={}, message={}", request.getUserId(), request.getMessage());
        long startTime = System.currentTimeMillis();
        
        try {
            ChatResponse response = aiCustomerService.processChat(request);
            long duration = System.currentTimeMillis() - startTime;
            log.info("异步聊天请求处理完成: userId={}, 耗时={}ms", request.getUserId(), duration);
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            log.error("异步聊天请求处理失败: userId={}, error={}", request.getUserId(), e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 批量异步处理聊天请求
     * 适用于批量导入或批量分析场景
     */
    @Async("chatTaskExecutor")
    public CompletableFuture<ChatResponse[]> processBatchChatAsync(ChatRequest[] requests) {
        log.info("批量异步处理聊天请求: count={}", requests.length);
        
        ChatResponse[] responses = new ChatResponse[requests.length];
        for (int i = 0; i < requests.length; i++) {
            try {
                responses[i] = aiCustomerService.processChat(requests[i]);
            } catch (Exception e) {
                log.error("批量处理失败: index={}, userId={}", i, requests[i].getUserId());
                responses[i] = ChatResponse.builder()
                        .response("处理失败: " + e.getMessage())
                        .userId(requests[i].getUserId())
                        .sessionId(requests[i].getSessionId())
                        .timestamp(System.currentTimeMillis())
                        .build();
            }
        }
        
        return CompletableFuture.completedFuture(responses);
    }
}
