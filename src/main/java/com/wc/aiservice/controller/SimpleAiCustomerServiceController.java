package com.wc.aiservice.controller;

import com.wc.aiservice.service.SimpleAiCustomerService;
import com.wc.aiservice.model.ChatRequest;
import com.wc.aiservice.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/customer-service")
public class SimpleAiCustomerServiceController {

    @Autowired
    private SimpleAiCustomerService aiCustomerService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            ChatResponse response = aiCustomerService.processChat(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ChatResponse errorResponse = ChatResponse.builder()
                    .response("抱歉，处理您的请求时出现错误: " + e.getMessage())
                    .build();
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Customer Service is running");
    }
}
