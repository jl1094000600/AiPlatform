package com.aipal.agent.image.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentConfig {
    private String code = "image-recognition-agent";
    private String name = "图像识别Agent";
    private String category = "图像识别";
    private String description = "提供图片内容识别、文件解析和图文理解能力";
    private String instanceId = "image-001";
    private String endpoint = "http://localhost:8082";
    private String modelCode = "gpt-4o";
    private Long heartbeatInterval = 30000L;
    private Platform platform = new Platform();

    public String getAgentCode() {
        return code;
    }

    public String getAgentName() {
        return name;
    }

    public String getPlatformUrl() {
        String heartbeatUrl = platform.getHeartbeatUrl();
        int apiIndex = heartbeatUrl.indexOf("/api/");
        return apiIndex > 0 ? heartbeatUrl.substring(0, apiIndex) : "http://localhost:8080";
    }

    public String getRegistryUrl() {
        return platform.getRegistryUrl();
    }

    public String getHeartbeatUrl() {
        return platform.getHeartbeatUrl();
    }

    public String getA2aUrl() {
        return platform.getA2aUrl();
    }

    @Data
    public static class Platform {
        private String registryUrl = "http://localhost:8080/api/v1/registry/agents";
        private String heartbeatUrl = "http://localhost:8080/api/v1/heartbeat/report";
        private String a2aUrl = "http://localhost:8080/api/v1/a2a/message";
        private String tenantCode = "aiplatform";
        private String heartbeatSecret;
    }
}
