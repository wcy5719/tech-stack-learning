package com.wc.aiservice.service;

import com.wc.aiservice.model.ChatRequest;
import com.wc.aiservice.model.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class BasicAiCustomerService {

    public ChatResponse processChat(ChatRequest request) {
        return ChatResponse.builder()
                .response("您好，我是AI客服，您咨询的问题是: " + request.getMessage())
                .userId(request.getUserId())
                .timestamp(System.currentTimeMillis())
                .sessionId(request.getSessionId())
                .build();
    }
}
