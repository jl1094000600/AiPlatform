package com.aipal.agent.image.config;

import com.aipal.agent.image.dto.A2AMessage;
import com.aipal.agent.image.service.A2AMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
public class AgentRegistryConfig implements ApplicationRunner {

    private final A2AMessageService a2aMessageService;
    private final AgentConfig agentConfig;

    public AgentRegistryConfig(
            A2AMessageService a2aMessageService,
            AgentConfig agentConfig) {
        this.a2aMessageService = a2aMessageService;
        this.agentConfig = agentConfig;
    }

    @Override
    public void run(ApplicationArguments args) {
        String agentCode = agentConfig.getAgentCode();
        String agentName = agentConfig.getAgentName();
        log.info("Registering agent: {} [{}]", agentCode, agentName);

        a2aMessageService.registerHandler(agentCode, createAgentHandler());

        log.info("Agent {} registered successfully", agentCode);
    }

    private Function<A2AMessage, A2AMessage> createAgentHandler() {
        return message -> {
            A2AMessage response = new A2AMessage();
            response.setSourceAgent(agentConfig.getAgentCode());
            response.setTargetAgent(message.getSourceAgent());
            response.setSessionId(message.getSessionId());
            response.setAction(A2AMessage.Action.respond);

            try {
                if (message.getPayload() != null && message.getPayload().containsKey("intent")) {
                    String intent = message.getPayload().get("intent").toString();
                    String result = "Agent " + agentConfig.getAgentName() + " processed intent: " + intent;
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
