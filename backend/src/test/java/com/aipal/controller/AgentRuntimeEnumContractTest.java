package com.aipal.controller;

import com.aipal.agent.runtime.trace.SpanStatus;
import com.aipal.agent.runtime.trace.SpanType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.aipal.agent.runtime.trace.SpanContractView;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R03 supplementary enum-compatibility contract tests.
 * Business-side AgentRuntimeContractTest covers path + DTO field contracts;
 * this class verifies SpanStatus / SpanType enum values stay within the
 * frontend-recognised label set (agentRuntimeContract.js SPAN_*_LABELS).
 */
class AgentRuntimeEnumContractTest {

    /** Status keys accepted by spanStatusLabel / SPAN_STATUS_LABELS. */
    private static final Set<String> FRONTEND_STATUS_KEYS = Set.of(
            "OK", "ERROR", "TIMEOUT", "CANCELLED", "WAITING",
            "SUCCESS", "SUCCEEDED", "FAILED", "RUNNING", "PENDING",
            "WAITING_USER", "WAITING_USER_INPUT", "ANSWERED", "QUEUED", "DENIED");

    /** Type keys accepted by spanTypeLabel / SPAN_TYPE_LABELS. */
    private static final Set<String> FRONTEND_TYPE_KEYS = Set.of(
            "RUN", "REPLY", "TOOL_CALL", "TOOL_RESULT",
            "ASK_USER_WAIT", "APPROVAL_WAIT", "BUDGET_CHECK",
            "run", "llm", "tool", "ask_user", "budget", "workspace", "pipeline");

    @Test
    void everySpanStatusHasFrontendLabel() {
        for (SpanStatus status : SpanStatus.values()) {
            assertTrue(FRONTEND_STATUS_KEYS.contains(status.name()),
                    "SpanStatus." + status.name() + " not in frontend SPAN_STATUS_LABELS keys");
        }
    }

    @Test
    void everySpanTypeHasFrontendLabel() {
        for (SpanType type : SpanType.values()) {
            assertTrue(FRONTEND_TYPE_KEYS.contains(type.name()),
                    "SpanType." + type.name() + " not in frontend SPAN_TYPE_LABELS keys");
        }
    }

    @Test
    void spanContractStatusRoundTripsEnumNames() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        for (SpanStatus status : SpanStatus.values()) {
            SpanContractView view = new SpanContractView(
                    "sp", "sp", "t", "T", "n",
                    status.name(), status.name(),
                    null, null, null, 0, 0,
                    null, null,
                    LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), 0L);
            JsonNode json = mapper.valueToTree(view);
            String statusText = json.get("status").asText();
            assertTrue(FRONTEND_STATUS_KEYS.contains(statusText),
                    "serialized status '" + statusText + "' must be frontend-recognised");
        }
    }
}
