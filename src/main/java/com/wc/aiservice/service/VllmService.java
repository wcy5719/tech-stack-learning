package com.wc.aiservice.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class VllmService {

    @Value("${vllm.api.url:http://localhost:8000}")
    private String vllmApiUrl;

    @Value("${vllm.model:qwen2.5-7b-instruct}")
    private String modelName;

    @Value("${vllm.max-tokens:1024}")
    private int maxTokens;

    @Value("${vllm.temperature:0.7}")
    private float temperature;

    private WebClient webClient;

    @Autowired(required = false)
    private VllmLoadBalancerService loadBalancerService;

    private boolean clusterMode = false;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(vllmApiUrl)
                .build();
        
        if (loadBalancerService != null) {
            try {
                loadBalancerService.initialize();
                Map<String, Object> status = loadBalancerService.getClusterStatus();
                clusterMode = (Boolean) status.getOrDefault("enabled", false) 
                        && (int) status.getOrDefault("activeInstances", 0) > 0;
                log.info("vLLM服务初始化: 集群模式={}, url={}, model={}", clusterMode, vllmApiUrl, modelName);
            } catch (Exception e) {
                log.warn("vLLM集群初始化失败，使用单实例模式: {}", e.getMessage());
                clusterMode = false;
            }
        } else {
            log.info("vLLM服务初始化: 单实例模式, url={}, model={}", vllmApiUrl, modelName);
        }
    }

    private WebClient getWebClient() {
        if (clusterMode && loadBalancerService != null) {
            return loadBalancerService.selectInstance();
        }
        return webClient;
    }

    private void releaseWebClient(String instanceName) {
        if (clusterMode && loadBalancerService != null && instanceName != null) {
            loadBalancerService.releaseInstance(instanceName);
        }
    }

    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "vllmService", fallbackMethod = "vllmFallback")
    public String generate(String prompt) {
        String instanceName = null;
        try {
            WebClient client = getWebClient();
            log.info("调用vLLM推理服务: 实例={}", clusterMode ? "cluster" : "single");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("prompt", prompt);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", temperature);
            requestBody.put("stream", false);

            Map<String, Object> response = client.post()
                    .uri("/v1/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    if (choice.containsKey("text")) {
                        return (String) choice.get("text");
                    }
                }
            }

            return "vLLM响应解析失败";
        } finally {
            if (instanceName != null) {
                releaseWebClient(instanceName);
            }
        }
    }

    @CircuitBreaker(name = "vllmService", fallbackMethod = "vllmFallback")
    public String chatComplete(List<Map<String, String>> messages) {
        String instanceName = null;
        try {
            WebClient client = getWebClient();
            log.info("调用vLLM Chat Completions API: model={}, 实例={}", modelName, clusterMode ? "cluster" : "single");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", temperature);
            requestBody.put("stream", false);

            log.debug("请求体: {}", requestBody);

            Map<String, Object> response = client.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            log.debug("vLLM响应: {}", response);

            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    if (choice.containsKey("message")) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> message = (Map<String, String>) choice.get("message");
                        return message.get("content");
                    }
                }
            }

            return "vLLM响应解析失败";
        } finally {
            if (instanceName != null) {
                releaseWebClient(instanceName);
            }
        }
    }

    public CompletableFuture<String> generateAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> generate(prompt));
    }

    public String vllmFallback(String prompt, Throwable t) {
        log.error("vLLM服务调用失败: {}", t.getMessage());
        return "当前AI服务暂时不可用，请稍后再试。";
    }

    public String vllmFallback(List<Map<String, String>> messages, Throwable t) {
        log.error("vLLM Chat服务调用失败: {}", t.getMessage());
        return "当前AI服务暂时不可用，请稍后再试。";
    }

    public boolean isHealthy() {
        try {
            WebClient client = clusterMode ? getWebClient() : webClient;
            String response = client.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return "ok".equalsIgnoreCase(response);
        } catch (Exception e) {
            log.warn("vLLM健康检查失败: {}", e.getMessage());
            return false;
        }
    }
}
