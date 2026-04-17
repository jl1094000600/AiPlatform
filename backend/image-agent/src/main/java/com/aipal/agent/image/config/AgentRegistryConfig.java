package com.aipal.agent.image.config;

import com.aipal.agent.image.dto.A2AMessage;
import com.aipal.agent.image.service.A2AMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
public class AgentRegistryConfig implements ApplicationRunner {

    private final A2AMessageService a2aMessageService;
    private final ChatClient.Builder chatClientBuilder;
    private final String agentCode;
    private final String agentName;

    public AgentRegistryConfig(
            A2AMessageService a2aMessageService,
            ChatClient.Builder chatClientBuilder,
            @Value("${image-agent.agent-code:image-recognition-agent}") String agentCode,
            @Value("${image-agent.agent-name:图像识别Agent}") String agentName) {
        this.a2aMessageService = a2aMessageService;
        this.chatClientBuilder = chatClientBuilder;
        this.agentCode = agentCode;
        this.agentName = agentName;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Registering agent: {} [{}]", agentCode, agentName);

        a2aMessageService.registerHandler(agentCode, createAgentHandler());

        log.info("Agent {} registered successfully", agentCode);
    }

    private Function<A2AMessage, A2AMessage> createAgentHandler() {
        return message -> {
            A2AMessage response = new A2AMessage();
            response.setSourceAgent(agentCode);
            response.setTargetAgent(message.getSourceAgent());
            response.setSessionId(message.getSessionId());
            response.setAction(A2AMessage.Action.respond);

            try {
                if (message.getPayload() != null && message.getPayload().containsKey("intent")) {
                    String intent = message.getPayload().get("intent").toString();
                    String result = "Agent " + agentName + " processed intent: " + intent;
                    response.setPayload(Map.of("status", "success", "result", result));
                } else {
                    response.setPayload(Map.of("status", "success", "message", "Image Agent acknowledged"));
                }
            } catch (Exception e) {
                response.setPayload(Map.of("status", "error", "error", e.getMessage()));
            }

            return response;
        };
    }
}
