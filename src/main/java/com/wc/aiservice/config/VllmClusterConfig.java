package com.wc.aiservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "vllm.cluster")
public class VllmClusterConfig {

    private boolean enabled = false;
    private String loadBalancer = "round-robin"; // round-robin, random, least-active
    private List<VllmInstance> instances;

    @Data
    public static class VllmInstance {
        private String name;
        private String url;
        private String model;
        private boolean enabled = true;
        private int weight = 1;
        private int maxConcurrentRequests = 10;
    }
}
