package com.aipal.service;

import com.aipal.entity.AiAgent;
import com.aipal.entity.AiAgentRuntimeConfig;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.mapper.AiAgentRuntimeConfigMapper;
import com.aipal.mapper.AiModelMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRuntimeConfigServiceTest {

    @Test
    void returnsDefaultConfigForAgentWithoutSavedConfig() {
        AiAgent agent = new AiAgent();
        agent.setId(1L);
        agent.setAgentCode("marketing-agent");

        AiAgentRuntimeConfigMapper configMapper = mock(AiAgentRuntimeConfigMapper.class);
        AiAgentMapper agentMapper = mock(AiAgentMapper.class);
        AiModelMapper modelMapper = mock(AiModelMapper.class);
        when(agentMapper.selectById(1L)).thenReturn(agent);
        when(configMapper.selectOne(any())).thenReturn(null);

        AgentRuntimeConfigService service = new AgentRuntimeConfigService(configMapper, agentMapper, modelMapper);
        AiAgentRuntimeConfig config = service.getOrDefaultByAgentId(1L);

        assertEquals("marketing-agent", config.getAgentCode());
        assertEquals(5, config.getTopK());
        assertEquals(0.7, config.getTemperature());
        assertEquals("input", config.getInputField());
        assertEquals("expectedOutput", config.getExpectedField());
    }

    @Test
    void rejectsInvalidTemperatureAndTopK() {
        AiAgent agent = new AiAgent();
        agent.setId(1L);
        agent.setAgentCode("marketing-agent");

        AiAgentRuntimeConfigMapper configMapper = mock(AiAgentRuntimeConfigMapper.class);
        AiAgentMapper agentMapper = mock(AiAgentMapper.class);
        AiModelMapper modelMapper = mock(AiModelMapper.class);
        when(agentMapper.selectById(1L)).thenReturn(agent);

        AgentRuntimeConfigService service = new AgentRuntimeConfigService(configMapper, agentMapper, modelMapper);
        AiAgentRuntimeConfig badTopK = new AiAgentRuntimeConfig();
        badTopK.setTopK(0);
        assertThrows(IllegalArgumentException.class, () -> service.saveForAgent(1L, badTopK));

        AiAgentRuntimeConfig badTemperature = new AiAgentRuntimeConfig();
        badTemperature.setTopK(5);
        badTemperature.setTemperature(3.0);
        assertThrows(IllegalArgumentException.class, () -> service.saveForAgent(1L, badTemperature));
    }
}
