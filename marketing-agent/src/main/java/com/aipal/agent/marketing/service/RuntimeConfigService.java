package com.aipal.agent.marketing.service;

import com.aipal.agent.marketing.config.AgentConfig;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeConfigService {

    private final AgentConfig agentConfig;
    private final RestTemplate restTemplate = new RestTemplate();
    private volatile RuntimeConfig currentConfig = RuntimeConfig.defaults();

    @PostConstruct
    public void init() {
        refreshConfig();
    }

    @Scheduled(fixedDelay = 30000)
    public void refreshConfig() {
        try {
            String url = agentConfig.getPlatformUrl() + "/api/v1/agent-config/" + agentConfig.getCode();
            PlatformResponse response = restTemplate.getForObject(url, PlatformResponse.class);
            if (response != null && Integer.valueOf(200).equals(response.getCode()) && response.getData() != null) {
                RuntimeConfig next = response.getData();
                next.applyDefaults();
                currentConfig = next;
                log.debug("Runtime config refreshed: model={}, topK={}, temperature={}",
                        next.getModelCode(), next.getTopK(), next.getTemperature());
            }
        } catch (Exception e) {
            log.debug("Keep previous runtime config because refresh failed: {}", e.getMessage());
        }
    }

    public RuntimeConfig getCurrentConfig() {
        return currentConfig;
    }

    @Data
    public static class RuntimeConfig {
        private Long modelId;
        private String modelCode;
        private Long datasetId;
        private Integer topK;
        private Double temperature;
        private String inputField;
        private String expectedField;

        static RuntimeConfig defaults() {
            RuntimeConfig config = new RuntimeConfig();
            config.applyDefaults();
            return config;
        }

        void applyDefaults() {
            if (topK == null) topK = 5;
            if (temperature == null) temperature = 0.7;
            if (inputField == null || inputField.isBlank()) inputField = "input";
            if (expectedField == null || expectedField.isBlank()) expectedField = "expectedOutput";
        }
    }

    @Data
    public static class PlatformResponse {
        private Integer code;
        private String message;
        private RuntimeConfig data;
    }
}
