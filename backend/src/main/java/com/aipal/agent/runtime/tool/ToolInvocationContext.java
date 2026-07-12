package com.aipal.agent.runtime.tool;

public record ToolInvocationContext(Long tenantId, Long runId, Long agentId, Long agentVersionId, Long stepId,
                                    Long ownerUserId, String projectKey, String traceId, String callId,
                                    String toolName, Long modelId, Long promptId, Long promptVersionId) {
}
