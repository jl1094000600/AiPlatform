package com.aipal.agent.runtime.trace;

/** Terminal (or in-flight) state of one run span. */
public enum SpanStatus {
    OK,
    ERROR,
    TIMEOUT,
    CANCELLED,
    WAITING
}
