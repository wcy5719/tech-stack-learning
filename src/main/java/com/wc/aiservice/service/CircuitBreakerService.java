package com.wc.aiservice.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CircuitBreakerService {

    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackResponse")
    @Retry(name = "aiService")
    public String callAiService(String message) {
        log.info("调用AI服务: {}", message);
        throw new RuntimeException("AI服务暂时不可用");
    }

    public String fallbackResponse(String message, Throwable throwable) {
        log.warn("AI服务熔断降级触发: {}", throwable.getMessage());
        return "您好，当前服务繁忙，请稍后再试。或者您可以尝试以下常见问题：\n" +
               "1. 订单查询：请提供订单号\n" +
               "2. 退款申请：请提供订单号和退款原因\n" +
               "3. 物流查询：请提供快递单号\n" +
               "感谢您的理解！";
    }

    @CircuitBreaker(name = "redisService", fallbackMethod = "redisFallback")
    public Object accessRedis(String key) {
        throw new RuntimeException("Redis服务不可用");
    }

    public Object redisFallback(String key, Throwable throwable) {
        log.warn("Redis服务降级: {}", throwable.getMessage());
        return null;
    }

    @CircuitBreaker(name = "vllmService", fallbackMethod = "vllmFallback")
    public String callVllm(String prompt) {
        throw new RuntimeException("vLLM服务不可用");
    }

    public String vllmFallback(String prompt, Throwable throwable) {
        log.error("vLLM服务熔断: {}", throwable.getMessage());
        return "当前AI推理服务暂时不可用，请稍后再试。";
    }
}
