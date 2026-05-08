package com.aipal.agent.marketing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentConfig {
    private String code = "marketing-agent";
    private String name = "市场营销Agent";
    private String instanceId = "default";
    private PlatformConfig platform = new PlatformConfig();

    public String getPlatformUrl() {
        String heartbeatUrl = platform.getHeartbeatUrl();
        int apiIndex = heartbeatUrl.indexOf("/api/");
        return apiIndex > 0 ? heartbeatUrl.substring(0, apiIndex) : "http://localhost:8080";
    }

    @Data
    public static class PlatformConfig {
        private String registryUrl = "http://localhost:8080/api/v1/registry/agents";
        private String heartbeatUrl = "http://localhost:8080/api/v1/heartbeat/report";
        private String a2aUrl = "http://localhost:8080/api/v1/a2a/message";
    }
}
