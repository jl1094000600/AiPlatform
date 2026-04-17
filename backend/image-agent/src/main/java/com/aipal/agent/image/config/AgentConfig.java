package com.aipal.agent.image.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "image-agent")
public class AgentConfig {
    private String agentCode = "image-recognition-agent";
    private String agentName = "图像识别Agent";
    private String modelCode = "gpt-4o";
    private Long heartbeatInterval = 30000L;
    private String platformUrl = "http://localhost:8080";
}
