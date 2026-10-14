package com.aipal.agent.runtime.tool.builtin;

import com.aipal.agent.runtime.tool.AgentTool;
import com.aipal.agent.runtime.tool.ToolInvocationContext;
import com.aipal.agent.runtime.tool.ToolResult;
import com.aipal.entity.AgentTask;
import com.aipal.service.AgentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** task.update: updates the status of one task of the current agent run. */
@Component
@RequiredArgsConstructor
public class UpdateTaskTool implements AgentTool {

    public static final String TOOL_NAME = "task.update";

    private final AgentTaskService agentTaskService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public ToolResult execute(ToolInvocationContext context, Object input) {
        Map<?, ?> payload = input instanceof Map<?, ?> map ? map : Map.of();
        Long taskId = requiredTaskId(payload);
        String status = requiredString(payload, "status");
        Object noteValue = payload.get("note");
        String note = noteValue == null ? null : String.valueOf(noteValue);
        AgentTask task = agentTaskService.updateModelTaskStatus(context.tenantId(), context.runId(),
                taskId, status, note);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("taskId", task.getId());
        output.put("status", task.getStatus());
        return new ToolResult(output, "task.update set task " + task.getId() + " to " + task.getStatus(), 0, 0);
    }

    private Long requiredTaskId(Map<?, ?> payload) {
        Object value = payload.get("taskId");
        if (value == null) throw new IllegalArgumentException("taskId is required");
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("taskId must be an integer");
        }
    }

    private String requiredString(Map<?, ?> payload, String field) {
        Object value = payload.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return String.valueOf(value).trim();
    }
}
