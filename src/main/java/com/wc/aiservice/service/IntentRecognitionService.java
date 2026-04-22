package com.wc.aiservice.service;

import org.springframework.stereotype.Service;

@Service
public class IntentRecognitionService {
    
    public String recognizeIntent(String message) {
        // 简单的意图识别逻辑
        String lowerMessage = message.toLowerCase();
        
        if (lowerMessage.contains("订单") || lowerMessage.contains("购买")) {
            return "order_inquiry";
        } else if (lowerMessage.contains("退款") || lowerMessage.contains("退钱")) {
            return "refund_request";
        } else if (lowerMessage.contains("发货") || lowerMessage.contains("物流")) {
            return "shipping_status";
        } else if (lowerMessage.contains("价格") || lowerMessage.contains("多少钱")) {
            return "price_inquiry";
        } else if (lowerMessage.contains("优惠") || lowerMessage.contains("折扣")) {
            return "discount_inquiry";
        } else if (lowerMessage.contains("售后") || lowerMessage.contains("保修")) {
            return "after_sales";
        } else {
            return "general_inquiry";
        }
    }
}
