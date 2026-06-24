package com.aipal.agent.runtime;

public enum AgentStepType {
    SNAPSHOT,
    PLAN,
    TOOL_CALL,
    DELEGATE,
    OBSERVATION,
    WAIT_APPROVAL,
    FINAL
}
