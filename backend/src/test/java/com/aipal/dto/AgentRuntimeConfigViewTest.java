package com.aipal.dto;

import com.aipal.entity.AiAgentRuntimeConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeConfigViewTest {

    @Test
    void redactsPromptBodiesButKeepsPresenceFlags() {
        AiAgentRuntimeConfig config = new AiAgentRuntimeConfig();
        config.setAgentId(7L);
        config.setAgentCode("agent-code");
        config.setSystemPrompt("secret system prompt");
        config.setUserPromptTemplate("secret user prompt");

        AgentRuntimeConfigView view = AgentRuntimeConfigView.from(config);

        assertEquals(7L, view.agentId());
        assertEquals("agent-code", view.agentCode());
        assertTrue(view.hasSystemPrompt());
        assertTrue(view.hasUserPromptTemplate());
    }
}
