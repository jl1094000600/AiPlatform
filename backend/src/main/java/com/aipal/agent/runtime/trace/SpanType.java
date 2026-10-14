package com.aipal.agent.runtime.trace;

/** Kind of work represented by one run span. */
public enum SpanType {
    RUN,
    REPLY,
    TOOL_CALL,
    TOOL_RESULT,
    ASK_USER_WAIT,
    APPROVAL_WAIT,
    BUDGET_CHECK
}
