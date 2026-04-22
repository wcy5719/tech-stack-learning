package com.wc.aiservice.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    @NotBlank(message = "消息内容不能为空")
    private String message;
    
    private String sessionId;
    
    private boolean multiTurn = true;
    
    private String idempotencyKey;
}
