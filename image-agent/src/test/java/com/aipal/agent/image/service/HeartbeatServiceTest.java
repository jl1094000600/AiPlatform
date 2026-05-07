package com.aipal.agent.image.service;

import com.aipal.agent.image.config.AgentConfig;
import com.aipal.agent.image.dto.HeartbeatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class HeartbeatServiceTest {

    @Test
    void buildsRegistrationRequestWithPlatformVisibleAgentCode() {
        AgentConfig config = createConfig();
        HeartbeatService service = new HeartbeatService(config, mock(RestTemplate.class));

        Map<String, Object> request = service.buildRegistrationRequest();

        assertEquals("image-recognition-agent", request.get("agentCode"));
        assertEquals("图像识别Agent", request.get("agentName"));
        assertEquals("image-001", request.get("instanceId"));
        assertEquals("http://localhost:8082", request.get("apiUrl"));
        assertEquals("/api/image-agent/health", request.get("healthEndpoint"));
    }

    @Test
    void buildsHeartbeatRequestWithAgentCodeAndInstanceId() {
        AgentConfig config = createConfig();
        HeartbeatService service = new HeartbeatService(config, mock(RestTemplate.class));

        HeartbeatRequest request = service.buildHeartbeatRequest();

        assertEquals("image-recognition-agent", request.getAgentCode());
        assertEquals("image-001", request.getInstanceId());
        assertEquals("http://localhost:8082", request.getEndpoint());
        assertTrue(request.getCapabilities().contains("image_recognition"));
        assertEquals("图像识别Agent", request.getMetadata().get("agentName"));
    }

    private AgentConfig createConfig() {
        AgentConfig config = new AgentConfig();
        config.setCode("image-recognition-agent");
        config.setName("图像识别Agent");
        config.setCategory("图像识别");
        config.setDescription("提供图片内容识别、文件解析和图文理解能力");
        config.setInstanceId("image-001");
        config.setEndpoint("http://localhost:8082");
        config.setHeartbeatInterval(30000L);
        return config;
    }
}
