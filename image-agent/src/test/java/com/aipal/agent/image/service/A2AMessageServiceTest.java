package com.aipal.agent.image.service;

import com.aipal.agent.image.config.AgentConfig;
import com.aipal.agent.image.dto.A2AMessage;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class A2AMessageServiceTest {

    @Test
    void routesInboundMessageByTargetAgent() {
        AgentConfig config = new AgentConfig();
        config.setCode("image-recognition-agent");
        A2AMessageService service = new A2AMessageService(mock(RedisTemplate.class), config);
        service.registerHandler("image-recognition-agent", message -> {
            A2AMessage response = new A2AMessage();
            response.setSourceAgent("image-recognition-agent");
            response.setTargetAgent(message.getSourceAgent());
            response.setSessionId(message.getSessionId());
            response.setAction(A2AMessage.Action.respond);
            response.setPayload(Map.of("status", "success"));
            return response;
        });

        A2AMessage message = new A2AMessage();
        message.setSourceAgent("marketing-agent");
        message.setTargetAgent("image-recognition-agent");
        message.setSessionId("session-1");
        message.setAction(A2AMessage.Action.request);

        A2AMessage response = service.handleMessage(message);

        assertEquals("image-recognition-agent", response.getSourceAgent());
        assertEquals("marketing-agent", response.getTargetAgent());
        assertEquals("success", response.getPayload().get("status"));
    }
}
