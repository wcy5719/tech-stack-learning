package com.wc.aiservice.service;

import com.wc.aiservice.model.ChatRequest;
import com.wc.aiservice.model.ChatResponse;
import com.wc.aiservice.config.AiServiceConfig;
import com.wc.aiservice.config.AiMetricsCollector;
import com.wc.aiservice.model.ChatHistory;
import com.wc.aiservice.model.ChatSession;
import com.wc.aiservice.repository.ChatHistoryRepository;
import com.wc.aiservice.repository.ChatSessionRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCustomerService {

    private final VllmService vllmService;
    private final AiServiceConfig aiServiceConfig;
    private final IntentRecognitionService intentRecognitionService;
    private final TwoLevelCacheService twoLevelCacheService;
    private final IdempotencyService idempotencyService;
    private final SensitiveWordFilterService sensitiveWordFilterService;
    private final MultiTurnConversationService multiTurnConversationService;
    private final BloomFilterService bloomFilterService;
    private final CacheSyncService cacheSyncService;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final AiMetricsCollector metricsCollector;

    private static final String CACHE_KEY_PREFIX = "ai_response:";
    private static final String USER_SESSION_KEY_PREFIX = "user_session:";

    public ChatResponse processChat(ChatRequest request) {
        metricsCollector.recordRequest();
        Timer.Sample totalTimer = Timer.start();

        try {
            long startTime = System.currentTimeMillis();

            SensitiveWordFilterService.FilterResult filterResult =
                    sensitiveWordFilterService.filter(request.getMessage());
            if (!filterResult.passed()) {
                log.warn("敏感词过滤拦截: userId={}, message={}", request.getUserId(), request.getMessage());
            }

            String intent = intentRecognitionService.recognizeIntent(filterResult.filteredContent());

            String cacheKey = intent + ":" + filterResult.filteredContent().hashCode();

            // 1. 先检查缓存（幂等检查在缓存检查之后）
            if (bloomFilterService.mightContain(cacheKey)) {
                Timer.Sample cacheTimer = Timer.start();
                ChatResponse cachedResponse = twoLevelCacheService.get(cacheKey);
                cacheTimer.stop(metricsCollector.getCacheGetTimer());
                if (cachedResponse != null) {
                    log.info("缓存命中: key={}", cacheKey);
                    cachedResponse.setTimestamp(System.currentTimeMillis());
                    metricsCollector.recordCacheHit();
                    metricsCollector.recordRequestCompleted();
                    totalTimer.stop(metricsCollector.getTotalRequestTimer());
                    return cachedResponse;
                }
            }
            metricsCollector.recordCacheMiss();
            bloomFilterService.put(cacheKey);

            // 2. 幂等性检查（仅在实际处理前）
            String idempotencyKey = idempotencyService.generateRequestHash(
                    request.getUserId(), request.getSessionId(), request.getMessage());

            if (!idempotencyService.checkAndSetIdempotencyKey(idempotencyKey)) {
                // 如果是重复请求但缓存已存在，返回缓存
                ChatResponse cachedResponse = twoLevelCacheService.get(cacheKey);
                if (cachedResponse != null) {
                    cachedResponse.setTimestamp(System.currentTimeMillis());
                    metricsCollector.recordCacheHit();
                    metricsCollector.recordRequestCompleted();
                    totalTimer.stop(metricsCollector.getTotalRequestTimer());
                    return cachedResponse;
                }
                metricsCollector.recordError("duplicate_request");
                metricsCollector.recordRequestCompleted();
                totalTimer.stop(metricsCollector.getTotalRequestTimer());
                return ChatResponse.builder()
                        .response("检测到重复请求，请稍后再试")
                        .userId(request.getUserId())
                        .sessionId(request.getSessionId())
                        .timestamp(System.currentTimeMillis())
                        .cached(false)
                        .build();
            }

            String fullMessage;
            if (request.isMultiTurn()) {
                fullMessage = multiTurnConversationService.buildContextWithHistory(
                        request.getSessionId(), filterResult.filteredContent());
                multiTurnConversationService.addMessage(request.getSessionId(), "user", filterResult.filteredContent());
            } else {
                fullMessage = buildFullChatContext(request, intent, filterResult.filteredContent());
            }

            String responseContent = callAiService(fullMessage, request);

            multiTurnConversationService.addMessage(request.getSessionId(), "assistant", responseContent);

            ChatResponse response = ChatResponse.builder()
                    .response(responseContent)
                    .userId(request.getUserId())
                    .sessionId(request.getSessionId())
                    .timestamp(System.currentTimeMillis())
                    .intent(intent)
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();

            twoLevelCacheService.put(cacheKey, response);
            cacheSyncService.publishCacheUpdate(cacheKey, "UPDATE", response);

            saveChatHistory(request, response);
            updateSession(request.getUserId(), request.getSessionId());

            metricsCollector.recordRequestCompleted();
            totalTimer.stop(metricsCollector.getTotalRequestTimer());

            return response;
        } catch (Exception e) {
            metricsCollector.recordError("chat_processing");
            metricsCollector.recordRequestCompleted();
            totalTimer.stop(metricsCollector.getTotalRequestTimer());
            throw e;
        }
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "callAiServiceFallback")
    @Retry(name = "aiService")
    private String callAiService(String fullMessage, ChatRequest request) {
        Timer.Sample inferenceTimer = metricsCollector.startInferenceTimer();
        try {
            String result = vllmService.chatComplete(buildMessages(fullMessage, request));
            metricsCollector.recordInferenceTime(inferenceTimer);
            return result;
        } catch (Exception e) {
            metricsCollector.recordError("inference_failed");
            throw e;
        }
    }

    private String callAiServiceFallback(String fullMessage, ChatRequest request, Throwable throwable) {
        log.warn("vLLM调用失败，使用降级回复: {}", throwable.getMessage());
        metricsCollector.recordError("vllm_call_fallback");
        return "当前AI推理服务暂时不可用，已切换到基础回复模式。";
    }

    private List<Map<String, String>> buildMessages(String fullMessage, ChatRequest request) {
        return List.of(
                Map.of("role", "system", "content", "你是一个专业的AI客服助手，提供准确、友好的客服支持。"),
                Map.of("role", "user", "content", fullMessage)
        );
    }

    private String buildFullChatContext(ChatRequest request, String intent, String filteredMessage) {
        StringBuilder context = new StringBuilder();

        switch (intent) {
            case "order_inquiry" -> context.append("你是一个专业的订单客服助手，用户询问订单相关问题。请提供准确的订单信息。");
            case "refund_request" -> context.append("你是一个专业的退款客服助手，用户请求退款。请按照公司退款流程处理。");
            case "shipping_status" -> context.append("你是一个专业的物流客服助手，用户询问发货状态。请提供准确的物流信息。");
            case "price_inquiry" -> context.append("你是一个专业的价格客服助手，用户询问产品价格。请提供准确的价格信息。");
            case "discount_inquiry" -> context.append("你是一个专业的优惠客服助手，用户询问优惠活动。请提供最新的优惠信息。");
            case "after_sales" -> context.append("你是一个专业的售后客服助手，用户询问售后服务。请提供清晰的售后流程。");
            default -> context.append("你是一个专业的AI客服助手，提供准确、友好的客服支持。");
        }

        context.append("\n用户问题: ").append(filteredMessage);
        context.append("\n当前时间: ").append(new java.util.Date());
        context.append("\n请以专业、礼貌的方式回答这个问题。");

        return context.toString();
    }

    private void saveChatHistory(ChatRequest request, ChatResponse response) {
        try {
            ChatHistory history = new ChatHistory();
            history.setUserId(request.getUserId());
            history.setSessionId(request.getSessionId() != null ? request.getSessionId() : java.util.UUID.randomUUID().toString());
            history.setUserMessage(request.getMessage());
            history.setAiResponse(response.getResponse());
            history.setIntent(response.getIntent());
            history.setResponseTimeMs(response.getResponseTimeMs());
            chatHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("保存聊天历史失败: {}", e.getMessage());
        }
    }

    private void updateSession(String userId, String sessionId) {
        String finalSessionId = sessionId != null ? sessionId : java.util.UUID.randomUUID().toString();
        
        chatSessionRepository.findBySessionIdAndUserId(finalSessionId, userId)
                .ifPresentOrElse(
                        session -> {
                            session.setMessageCount(session.getMessageCount() + 1);
                            session.setLastMessageTime(java.time.LocalDateTime.now());
                            chatSessionRepository.save(session);
                        },
                        () -> {
                            ChatSession newSession = new ChatSession();
                            newSession.setSessionId(finalSessionId);
                            newSession.setUserId(userId);
                            newSession.setMessageCount(1);
                            newSession.setStatus("ACTIVE");
                            chatSessionRepository.save(newSession);
                        }
                );
    }

    public ChatResponse fallbackResponse(ChatRequest request, Throwable throwable) {
        log.error("AI服务熔断降级: {}", throwable.getMessage());
        return ChatResponse.builder()
                .response("您好，当前服务繁忙，请稍后再试。或者您可以尝试以下常见问题：\n" +
                        "1. 订单查询：请提供订单号\n" +
                        "2. 退款申请：请提供订单号和退款原因\n" +
                        "3. 物流查询：请提供快递单号\n" +
                        "感谢您的理解！")
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
