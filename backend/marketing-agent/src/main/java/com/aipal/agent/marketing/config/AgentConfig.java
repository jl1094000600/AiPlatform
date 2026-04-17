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

    @Data
    public static class PlatformConfig {
        private String heartbeatUrl = "http://localhost:8080/api/v1/heartbeat/report";
        private String a2aUrl = "http://localhost:8080/api/v1/a2a/message";
    }
}
