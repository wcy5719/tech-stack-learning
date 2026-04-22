package com.wc.aiservice;

import com.wc.aiservice.service.IntentRecognitionService;
import com.wc.aiservice.model.ChatRequest;
import com.wc.aiservice.model.ChatResponse;
import com.wc.aiservice.service.BasicAiCustomerService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnitTests {

    @Test
    void testIntentRecognition() {
        IntentRecognitionService service = new IntentRecognitionService();
        
        assertEquals("order_inquiry", service.recognizeIntent("我的订单在哪"));
        assertEquals("refund_request", service.recognizeIntent("我要退款"));
        assertEquals("shipping_status", service.recognizeIntent("物流查询"));
        assertEquals("price_inquiry", service.recognizeIntent("价格多少"));
        assertEquals("discount_inquiry", service.recognizeIntent("有什么优惠"));
        assertEquals("after_sales", service.recognizeIntent("售后保修"));
        assertEquals("general_inquiry", service.recognizeIntent("你好"));
    }

    @Test
    void testBasicAiCustomerService() {
        BasicAiCustomerService service = new BasicAiCustomerService();
        
        ChatRequest request = new ChatRequest();
        request.setUserId("user123");
        request.setMessage("测试消息");
        request.setSessionId("session1");
        
        ChatResponse response = service.processChat(request);
        
        assertNotNull(response);
        assertEquals("user123", response.getUserId());
        assertTrue(response.getResponse().contains("测试消息"));
        assertTrue(response.getTimestamp() > 0);
    }

    @Test
    void testChatRequestValidation() {
        ChatRequest request = new ChatRequest();
        request.setUserId("user1");
        request.setMessage("Hello");
        request.setSessionId("session1");
        request.setMultiTurn(true);
        
        assertEquals("user1", request.getUserId());
        assertEquals("Hello", request.getMessage());
        assertTrue(request.isMultiTurn());
    }

    @Test
    void testChatResponseBuilder() {
        ChatResponse response = ChatResponse.builder()
                .response("Test response")
                .userId("user1")
                .sessionId("session1")
                .timestamp(System.currentTimeMillis())
                .intent("test")
                .responseTimeMs(100L)
                .cached(false)
                .build();
        
        assertNotNull(response);
        assertEquals("Test response", response.getResponse());
        assertEquals("user1", response.getUserId());
        assertEquals("test", response.getIntent());
        assertFalse(response.isCached());
    }
}
