package com.aipal.dto;

import com.aipal.entity.AiAgentRuntimeConfig;

public record AgentRuntimeConfigView(
        Long id,
        Long agentId,
        String agentCode,
        Long modelId,
        Long datasetId,
        Integer topK,
        Double temperature,
        String inputField,
        String expectedField,
        Long promptId,
        Long promptVersionId,
        Integer enabled,
        String modelCode,
        boolean hasSystemPrompt,
        boolean hasUserPromptTemplate
) {
    public static AgentRuntimeConfigView from(AiAgentRuntimeConfig config) {
        return new AgentRuntimeConfigView(
                config.getId(),
                config.getAgentId(),
                config.getAgentCode(),
                config.getModelId(),
                config.getDatasetId(),
                config.getTopK(),
                config.getTemperature(),
                config.getInputField(),
                config.getExpectedField(),
                config.getPromptId(),
                config.getPromptVersionId(),
                config.getEnabled(),
                config.getModelCode(),
                hasText(config.getSystemPrompt()),
                hasText(config.getUserPromptTemplate())
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
