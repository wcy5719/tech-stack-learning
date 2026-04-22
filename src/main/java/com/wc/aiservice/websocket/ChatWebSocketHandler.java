package com.wc.aiservice.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wc.aiservice.model.ChatRequest;
import com.wc.aiservice.model.ChatResponse;
import com.wc.aiservice.service.AiCustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final AiCustomerService aiCustomerService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket连接建立: sessionId={}, 当前连接数={}", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            log.debug("收到WebSocket消息: sessionId={}, payload={}", session.getId(), payload);
            
            ChatRequest chatRequest = objectMapper.readValue(payload, ChatRequest.class);
            
            ChatResponse response = aiCustomerService.processChat(chatRequest);
            
            String responseJson = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(responseJson));
            
            log.info("WebSocket响应发送: sessionId={}", session.getId());
        } catch (Exception e) {
            log.error("WebSocket消息处理失败: sessionId={}, error={}", session.getId(), e.getMessage());
            try {
                ChatResponse errorResponse = ChatResponse.builder()
                        .response("消息处理失败: " + e.getMessage())
                        .sessionId(session.getId())
                        .timestamp(System.currentTimeMillis())
                        .build();
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
            } catch (IOException ex) {
                log.error("发送错误消息失败: {}", ex.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WebSocket连接关闭: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket传输错误: sessionId={}", session.getId(), exception);
        sessions.remove(session.getId());
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}
