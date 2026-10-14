package com.aipal.agent.runtime.tool.builtin;

import com.aipal.agent.runtime.tool.AgentTool;
import com.aipal.agent.runtime.tool.ToolInvocationContext;
import com.aipal.agent.runtime.tool.ToolResult;
import com.aipal.entity.AgentTask;
import com.aipal.service.AgentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** task.list: lists tasks belonging to the current agent run. */
@Component
@RequiredArgsConstructor
public class ListTasksTool implements AgentTool {

    public static final String TOOL_NAME = "task.list";

    private final AgentTaskService agentTaskService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public ToolResult execute(ToolInvocationContext context, Object input) {
        Map<?, ?> payload = input instanceof Map<?, ?> map ? map : Map.of();
        Object statusValue = payload.get("status");
        String status = statusValue == null || String.valueOf(statusValue).isBlank()
                ? null : String.valueOf(statusValue);
        List<AgentTask> tasks = agentTaskService.listModelTasks(context.tenantId(), context.runId(), status);
        List<Map<String, Object>> items = tasks.stream().map(this::toItem).toList();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("tasks", items);
        output.put("total", items.size());
        return new ToolResult(output, "task.list returned " + items.size() + " task(s)", 0, 0);
    }

    private Map<String, Object> toItem(AgentTask task) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("taskId", task.getId());
        item.put("parentTaskId", task.getParentTaskId());
        item.put("taskType", task.getTaskType());
        item.put("status", task.getStatus());
        item.put("errorMessage", task.getErrorMessage());
        item.put("createTime", task.getCreateTime());
        item.put("startTime", task.getStartTime());
        item.put("endTime", task.getEndTime());
        return item;
    }
}
