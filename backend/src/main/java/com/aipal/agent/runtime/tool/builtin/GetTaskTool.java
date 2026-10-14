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

/** task.get: returns one task of the current agent run. */
@Component
@RequiredArgsConstructor
public class GetTaskTool implements AgentTool {

    public static final String TOOL_NAME = "task.get";

    private final AgentTaskService agentTaskService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public ToolResult execute(ToolInvocationContext context, Object input) {
        Map<?, ?> payload = input instanceof Map<?, ?> map ? map : Map.of();
        Long taskId = requiredTaskId(payload);
        AgentTask task = agentTaskService.getModelTask(context.tenantId(), context.runId(), taskId);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("taskId", task.getId());
        item.put("parentTaskId", task.getParentTaskId());
        item.put("taskType", task.getTaskType());
        item.put("status", task.getStatus());
        item.put("attemptCount", task.getAttemptCount());
        item.put("maxAttempts", task.getMaxAttempts());
        item.put("errorMessage", task.getErrorMessage());
        item.put("createTime", task.getCreateTime());
        item.put("startTime", task.getStartTime());
        item.put("endTime", task.getEndTime());
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("task", item);
        return new ToolResult(output, "task.get returned task " + task.getId(), 0, 0);
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
}
