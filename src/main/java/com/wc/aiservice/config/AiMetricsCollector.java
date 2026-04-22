package com.wc.aiservice.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class AiMetricsCollector {

    private MeterRegistry meterRegistry;
    
    private Counter totalRequestsCounter;
    private Counter cacheHitCounter;
    private Counter cacheMissCounter;
    private Counter rateLimitedCounter;
    private Counter errorCounter;
    
    private Timer aiInferenceTimer;
    private Timer cacheGetTimer;
    private Timer totalRequestTimer;
    
    private AtomicInteger activeRequestsGauge;
    private AtomicLong cacheHitRateGauge;
    
    private double totalCacheHits = 0;
    private double totalCacheRequests = 0;

    public AiMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        totalRequestsCounter = Counter.builder("ai.requests.total")
                .description("Total number of AI service requests")
                .register(meterRegistry);

        cacheHitCounter = Counter.builder("ai.cache.hits")
                .description("Total number of cache hits")
                .register(meterRegistry);

        cacheMissCounter = Counter.builder("ai.cache.misses")
                .description("Total number of cache misses")
                .register(meterRegistry);

        rateLimitedCounter = Counter.builder("ai.rate_limited")
                .description("Total number of rate limited requests")
                .register(meterRegistry);

        errorCounter = Counter.builder("ai.errors")
                .description("Total number of errors")
                .tag("type", "general")
                .register(meterRegistry);

        aiInferenceTimer = Timer.builder("ai.inference.duration")
                .description("Duration of AI inference calls")
                .register(meterRegistry);

        cacheGetTimer = Timer.builder("ai.cache.get.duration")
                .description("Duration of cache get operations")
                .register(meterRegistry);

        totalRequestTimer = Timer.builder("ai.request.total.duration")
                .description("Total duration of request processing")
                .register(meterRegistry);

        activeRequestsGauge = new AtomicInteger(0);
        Gauge.builder("ai.requests.active", activeRequestsGauge, AtomicInteger::get)
                .description("Number of currently active requests")
                .register(meterRegistry);

        cacheHitRateGauge = new AtomicLong(0);
        Gauge.builder("ai.cache.hit_rate", cacheHitRateGauge, AtomicLong::get)
                .description("Current cache hit rate percentage")
                .register(meterRegistry);

        log.info("AI Metrics collector initialized");
    }

    public void recordRequest() {
        totalRequestsCounter.increment();
        activeRequestsGauge.incrementAndGet();
    }

    public void recordRequestCompleted() {
        activeRequestsGauge.decrementAndGet();
    }

    public void recordCacheHit() {
        cacheHitCounter.increment();
        totalCacheHits++;
        totalCacheRequests++;
        updateCacheHitRate();
    }

    public void recordCacheMiss() {
        cacheMissCounter.increment();
        totalCacheRequests++;
        updateCacheHitRate();
    }

    public void recordRateLimited() {
        rateLimitedCounter.increment();
    }

    public void recordError(String type) {
        Counter.builder("ai.errors")
                .description("Total number of errors")
                .tag("type", type)
                .register(meterRegistry)
                .increment();
        errorCounter.increment();
    }

    public Timer.Sample startInferenceTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordInferenceTime(Timer.Sample sample) {
        sample.stop(aiInferenceTimer);
    }

    private void updateCacheHitRate() {
        if (totalCacheRequests > 0) {
            double rate = (totalCacheHits / totalCacheRequests) * 100;
            cacheHitRateGauge.set((long) rate);
        }
    }

    public Timer getAiInferenceTimer() {
        return aiInferenceTimer;
    }

    public Timer getCacheGetTimer() {
        return cacheGetTimer;
    }

    public Timer getTotalRequestTimer() {
        return totalRequestTimer;
    }
}
