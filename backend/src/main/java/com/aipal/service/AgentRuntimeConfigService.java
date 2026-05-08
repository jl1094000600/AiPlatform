package com.aipal.service;

import com.aipal.entity.AiAgent;
import com.aipal.entity.AiAgentRuntimeConfig;
import com.aipal.entity.AiModel;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.mapper.AiAgentRuntimeConfigMapper;
import com.aipal.mapper.AiModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentRuntimeConfigService {

    public static final int DEFAULT_TOP_K = 5;
    public static final double DEFAULT_TEMPERATURE = 0.7;
    public static final String DEFAULT_INPUT_FIELD = "input";
    public static final String DEFAULT_EXPECTED_FIELD = "expectedOutput";

    private final AiAgentRuntimeConfigMapper configMapper;
    private final AiAgentMapper agentMapper;
    private final AiModelMapper modelMapper;

    public AiAgentRuntimeConfig getOrDefaultByAgentId(Long agentId) {
        AiAgent agent = requireAgent(agentId);
        AiAgentRuntimeConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<AiAgentRuntimeConfig>().eq(AiAgentRuntimeConfig::getAgentId, agentId)
        );
        if (config != null) {
            fillDefaults(config);
            enrichModelCode(config);
            return config;
        }

        config = new AiAgentRuntimeConfig();
        config.setAgentId(agent.getId());
        config.setAgentCode(agent.getAgentCode());
        config.setTopK(DEFAULT_TOP_K);
        config.setTemperature(DEFAULT_TEMPERATURE);
        config.setInputField(DEFAULT_INPUT_FIELD);
        config.setExpectedField(DEFAULT_EXPECTED_FIELD);
        config.setEnabled(1);
        enrichModelCode(config);
        return config;
    }

    public AiAgentRuntimeConfig saveForAgent(Long agentId, AiAgentRuntimeConfig request) {
        AiAgent agent = requireAgent(agentId);
        if (request == null) {
            throw new IllegalArgumentException("Runtime config request is required");
        }
        validate(request);

        AiAgentRuntimeConfig existing = configMapper.selectOne(
                new LambdaQueryWrapper<AiAgentRuntimeConfig>().eq(AiAgentRuntimeConfig::getAgentId, agentId)
        );
        LocalDateTime now = LocalDateTime.now();
        AiAgentRuntimeConfig config = existing != null ? existing : new AiAgentRuntimeConfig();
        config.setAgentId(agent.getId());
        config.setAgentCode(agent.getAgentCode());
        config.setModelId(request.getModelId());
        config.setDatasetId(request.getDatasetId());
        config.setTopK(request.getTopK() != null ? request.getTopK() : DEFAULT_TOP_K);
        config.setTemperature(request.getTemperature() != null ? request.getTemperature() : DEFAULT_TEMPERATURE);
        config.setInputField(nonBlank(request.getInputField(), DEFAULT_INPUT_FIELD));
        config.setExpectedField(nonBlank(request.getExpectedField(), DEFAULT_EXPECTED_FIELD));
        config.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        config.setUpdateTime(now);

        if (existing == null) {
            config.setCreateTime(now);
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        fillDefaults(config);
        enrichModelCode(config);
        return config;
    }

    public AiAgentRuntimeConfig getEnabledByAgentCode(String agentCode) {
        AiAgentRuntimeConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<AiAgentRuntimeConfig>()
                        .eq(AiAgentRuntimeConfig::getAgentCode, agentCode)
                        .eq(AiAgentRuntimeConfig::getEnabled, 1)
        );
        if (config != null) {
            fillDefaults(config);
            enrichModelCode(config);
        }
        return config;
    }

    private AiAgent requireAgent(Long agentId) {
        AiAgent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent does not exist: " + agentId);
        }
        return agent;
    }

    private void validate(AiAgentRuntimeConfig config) {
        Integer topK = config.getTopK() != null ? config.getTopK() : DEFAULT_TOP_K;
        if (topK < 1 || topK > 100) {
            throw new IllegalArgumentException("topK must be between 1 and 100");
        }
        Double temperature = config.getTemperature() != null ? config.getTemperature() : DEFAULT_TEMPERATURE;
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("temperature must be between 0 and 2");
        }
    }

    private void fillDefaults(AiAgentRuntimeConfig config) {
        if (config.getTopK() == null) config.setTopK(DEFAULT_TOP_K);
        if (config.getTemperature() == null) config.setTemperature(DEFAULT_TEMPERATURE);
        if (config.getInputField() == null || config.getInputField().isBlank()) {
            config.setInputField(DEFAULT_INPUT_FIELD);
        }
        if (config.getExpectedField() == null || config.getExpectedField().isBlank()) {
            config.setExpectedField(DEFAULT_EXPECTED_FIELD);
        }
        if (config.getEnabled() == null) config.setEnabled(1);
    }

    private void enrichModelCode(AiAgentRuntimeConfig config) {
        if (config.getModelId() == null) {
            return;
        }
        AiModel model = modelMapper.selectById(config.getModelId());
        if (model != null) {
            config.setModelCode(model.getModelCode());
        }
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
