package com.aipal.agent.runtime;

/**
 * Durable lifecycle of one ask_user interaction. Terminal states never transition again;
 * a second respond against a terminal interaction is a no-op (idempotent).
 */
public enum AskInteractionStatus {
    PENDING,
    ANSWERED,
    TIMED_OUT,
    CANCELLED;

    public boolean terminal() {
        return this != PENDING;
    }
}
