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

/** task.create: creates a child task on the current agent run. */
@Component
@RequiredArgsConstructor
public class CreateTaskTool implements AgentTool {

    public static final String TOOL_NAME = "task.create";

    private final AgentTaskService agentTaskService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public ToolResult execute(ToolInvocationContext context, Object input) {
        Map<?, ?> payload = input instanceof Map<?, ?> map ? map : Map.of();
        String taskType = payload.get("taskType") == null ? "MODEL_TASK" : String.valueOf(payload.get("taskType")).trim();
        Object taskPayload = payload.get("payload");
        AgentTask task = agentTaskService.createModelTask(context.tenantId(), context.runId(), taskType, taskPayload);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("taskId", task.getId());
        output.put("status", task.getStatus());
        output.put("taskType", task.getTaskType());
        return new ToolResult(output, "task.create created task " + task.getId(), 0, 0);
    }
}
