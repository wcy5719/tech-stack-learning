package com.wc.aiservice.service;

import com.wc.aiservice.model.ChatRequest;
import com.wc.aiservice.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RequestDispatcher {
    
    @Autowired
    private AiCustomerService aiCustomerService;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public CompletableFuture<ChatResponse> dispatchRequest(ChatRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return aiCustomerService.processChat(request);
            } catch (Exception e) {
                throw new RuntimeException("处理请求时出错", e);
            }
        }, executorService);
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
