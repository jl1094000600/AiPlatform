package com.aipal.agent.runtime;

/** Lifecycle states for one durable, on-demand agent execution. */
public enum AgentRunStatus {
    QUEUED,
    RUNNING,
    WAITING_APPROVAL,
    /** Suspended by an in-flight ask_user tool call; distinct from pipeline artifact approval. */
    WAITING_USER_INPUT,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMEOUT;

    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }
}
