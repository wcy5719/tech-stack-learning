package com.wc.aiservice.service;

import com.wc.aiservice.config.VllmClusterConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VllmLoadBalancerService {

    private final VllmClusterConfig clusterConfig;
    private final Map<String, WebClient> webClients = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> activeRequests = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    public void initialize() {
        if (!clusterConfig.isEnabled() || clusterConfig.getInstances() == null) {
            log.info("vLLM集群未启用，使用单实例模式");
            return;
        }

        for (VllmClusterConfig.VllmInstance instance : clusterConfig.getInstances()) {
            if (instance.isEnabled()) {
                WebClient webClient = WebClient.builder()
                        .baseUrl(instance.getUrl())
                        .build();
                webClients.put(instance.getName(), webClient);
                activeRequests.put(instance.getName(), new AtomicInteger(0));
                log.info("注册vLLM实例: name={}, url={}, model={}", 
                        instance.getName(), instance.getUrl(), instance.getModel());
            }
        }

        log.info("vLLM集群初始化完成: 活跃实例数量={}", webClients.size());
    }

    /**
     * 选择下一个可用的vLLM实例
     */
    public WebClient selectInstance() {
        if (webClients.isEmpty()) {
            throw new IllegalStateException("没有可用的vLLM实例");
        }

        String selectedName;
        switch (clusterConfig.getLoadBalancer().toLowerCase()) {
            case "round-robin":
                selectedName = selectRoundRobin();
                break;
            case "least-active":
                selectedName = selectLeastActive();
                break;
            case "random":
                selectedName = selectRandom();
                break;
            default:
                selectedName = selectRoundRobin();
        }

        activeRequests.get(selectedName).incrementAndGet();
        log.debug("选择vLLM实例: {}, 当前活跃请求数: {}", 
                selectedName, activeRequests.get(selectedName).get());
        return webClients.get(selectedName);
    }

    /**
     * 请求完成后释放实例
     */
    public void releaseInstance(String instanceName) {
        AtomicInteger count = activeRequests.get(instanceName);
        if (count != null) {
            count.decrementAndGet();
        }
    }

    private String selectRoundRobin() {
        var enabledInstances = clusterConfig.getInstances().stream()
                .filter(VllmClusterConfig.VllmInstance::isEnabled)
                .collect(Collectors.toList());

        int index = Math.abs(roundRobinCounter.getAndIncrement() % enabledInstances.size());
        return enabledInstances.get(index).getName();
    }

    private String selectLeastActive() {
        return activeRequests.entrySet().stream()
                .min(Map.Entry.comparingByValue((a, b) -> Integer.compare(a.get(), b.get())))
                .map(Map.Entry::getKey)
                .orElse(webClients.keySet().iterator().next());
    }

    private String selectRandom() {
        int randomIndex = (int) (Math.random() * webClients.size());
        return webClients.keySet().stream()
                .skip(randomIndex)
                .findFirst()
                .orElse(webClients.keySet().iterator().next());
    }

    /**
     * 获取集群状态
     */
    public Map<String, Object> getClusterStatus() {
        return Map.of(
                "enabled", clusterConfig.isEnabled(),
                "loadBalancer", clusterConfig.getLoadBalancer(),
                "totalInstances", clusterConfig.getInstances() != null ? clusterConfig.getInstances().size() : 0,
                "activeInstances", webClients.size(),
                "instances", activeRequests.entrySet().stream().collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().get()
                        )
                )
        );
    }
}
