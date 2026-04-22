package com.wc.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.service")
public class AiServiceConfig {
    
    private int maxConcurrentRequests = 100;
    private int cacheExpirationSeconds = 3600;
    private RateLimit rateLimit = new RateLimit();
    
    // Getters and Setters
    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }
    
    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }
    
    public int getCacheExpirationSeconds() {
        return cacheExpirationSeconds;
    }
    
    public void setCacheExpirationSeconds(int cacheExpirationSeconds) {
        this.cacheExpirationSeconds = cacheExpirationSeconds;
    }
    
    public RateLimit getRateLimit() {
        return rateLimit;
    }
    
    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }
    
    public static class RateLimit {
        private int requestsPerMinute = 100;
        
        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }
        
        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
    }
}
