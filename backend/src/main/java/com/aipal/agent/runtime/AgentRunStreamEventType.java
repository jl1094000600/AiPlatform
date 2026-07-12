package com.aipal.agent.runtime;

public enum AgentRunStreamEventType {
    RUN_STARTED,
    MODEL_DELTA,
    TOOL_REQUESTED,
    PERMISSION_DECIDED,
    TOOL_STARTED,
    TOOL_DELTA,
    TOOL_COMPLETED,
    TOOL_FAILED,
    ARTIFACT_CREATED,
    APPROVAL_REQUIRED,
    RUN_COMPLETED,
    RUN_FAILED
}
