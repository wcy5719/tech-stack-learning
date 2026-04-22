package com.wc.aiservice.service;

import com.wc.aiservice.model.ChatResponse;
import com.wc.aiservice.config.AiServiceConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheWarmupService {

    private final TwoLevelCacheService twoLevelCacheService;
    private final BloomFilterService bloomFilterService;
    private final IntentRecognitionService intentRecognitionService;
    private final VllmService vllmService;
    private final AiServiceConfig aiServiceConfig;

    // 常见问题列表
    private static final List<String> COMMON_QUESTIONS = List.of(
            "如何查询订单？",
            "退款流程是什么？",
            "如何修改收货地址？",
            "有什么优惠活动？",
            "商品价格多少？",
            "发货时间多长？",
            "如何联系客服？",
            "如何申请售后？",
            "如何开具发票？",
            "如何取消订单？",
            "支持哪些支付方式？",
            "如何查看物流信息？",
            "商品有质量问题怎么办？",
            "如何修改账户信息？",
            "如何领取优惠券？"
    );

    @PostConstruct
    public void warmupCache() {
        // 异步执行缓存预热，不阻塞应用启动
        warmupCacheAsync();
    }

    @Async
    public void warmupCacheAsync() {
        log.info("开始缓存预热...");
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failCount = 0;

        for (String question : COMMON_QUESTIONS) {
            try {
                String intent = intentRecognitionService.recognizeIntent(question);
                String cacheKey = intent + ":" + question.hashCode();

                // 检查缓存是否已存在
                if (twoLevelCacheService.get(cacheKey) == null) {
                    // 调用 vLLM 生成答案
                    String answer = callVllmForAnswer(question, intent);

                    if (answer != null && !answer.isEmpty()) {
                        // 构建缓存响应
                        ChatResponse response = ChatResponse.builder()
                                .response(answer)
                                .userId("system")
                                .sessionId("warmup")
                                .timestamp(System.currentTimeMillis())
                                .intent(intent)
                                .responseTimeMs(0L)
                                .cached(true)
                                .build();

                        // 写入缓存
                        twoLevelCacheService.put(cacheKey, response);
                        bloomFilterService.put(cacheKey);
                        successCount++;
                        log.debug("预热成功: question={}, intent={}", question, intent);
                    } else {
                        failCount++;
                    }
                } else {
                    log.debug("缓存已存在，跳过: {}", question);
                }

                // 避免过快调用 vLLM
                Thread.sleep(1000);

            } catch (Exception e) {
                failCount++;
                log.warn("预热失败: question={}, error={}", question, e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("缓存预热完成: 成功={}, 失败={}, 总耗时={}ms", successCount, failCount, duration);
    }

    private String callVllmForAnswer(String question, String intent) {
        try {
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", "你是一个专业的AI客服助手，请简洁、准确地回答用户问题。回答控制在200字以内。"),
                    Map.of("role", "user", "content", question)
            );

            return vllmService.chatComplete(messages);
        } catch (Exception e) {
            log.error("调用vLLM生成答案失败: question={}, error={}", question, e.getMessage());
            return null;
        }
    }

    /**
     * 手动触发缓存预热（通过管理接口调用）
     */
    public Map<String, Object> triggerWarmup() {
        warmupCacheAsync();
        return Map.of(
                "status", "warmup_started",
                "message", "缓存预热已启动，请查看日志获取进度"
        );
    }

    /**
     * 获取预热状态
     */
    public Map<String, Object> getWarmupStatus() {
        return Map.of(
                "commonQuestionsCount", COMMON_QUESTIONS.size(),
                "bloomFilterStatus", "active",
                "cacheStatus", "active"
        );
    }
}
