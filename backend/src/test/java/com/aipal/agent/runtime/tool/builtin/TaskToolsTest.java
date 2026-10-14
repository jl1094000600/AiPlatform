package com.aipal.agent.runtime.tool.builtin;

import com.aipal.agent.runtime.AgentTaskStatus;
import com.aipal.agent.runtime.budget.RunBudgetGuard;
import com.aipal.agent.runtime.tool.ToolInvocationContext;
import com.aipal.agent.runtime.tool.ToolResult;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentTask;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentTaskMapper;
import com.aipal.service.AgentRunEventService;
import com.aipal.service.AgentTaskService;
import com.aipal.security.TenantTaskRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskToolsTest {

    private AgentTaskMapper taskMapper;
    private AgentRunMapper runMapper;
    private AgentTaskService taskService;
    private ToolInvocationContext context;

    @BeforeEach
    void setUp() {
        taskMapper = mock(AgentTaskMapper.class);
        runMapper = mock(AgentRunMapper.class);
        taskService = new AgentTaskService(taskMapper, runMapper, mock(TenantTaskRunner.class),
                mock(AgentRunEventService.class), new RunBudgetGuard(), new ObjectMapper());
        context = new ToolInvocationContext(1L, 99L, 11L, 22L, 5L, 7L, "project-a", "trace-1",
                "call-1", "task.create", null, null, null);
        AgentRun run = new AgentRun();
        run.setId(99L);
        run.setTenantId(1L);
        run.setMaxChildTasks(5);
        when(runMapper.selectById(99L)).thenReturn(run);
    }

    @Test
    void createTaskPersistsChildTask() {
        when(taskMapper.selectOne(any())).thenReturn(rootTask());
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(taskMapper.insert(any())).thenAnswer(invocation -> {
            AgentTask task = invocation.getArgument(0);
            task.setId(77L);
            return 1;
        });

        CreateTaskTool tool = new CreateTaskTool(taskService);
        ToolResult result = tool.execute(context, Map.of("taskType", "REVIEW", "payload", Map.of("k", "v")));

        assertEquals(77L, ((Map<?, ?>) result.output()).get("taskId"));
        assertEquals("QUEUED", ((Map<?, ?>) result.output()).get("status"));
        assertEquals("REVIEW", ((Map<?, ?>) result.output()).get("taskType"));
    }

    @Test
    void listTasksReturnsRunTasks() {
        when(taskMapper.selectList(any())).thenReturn(List.of(modelTask()));

        ListTasksTool tool = new ListTasksTool(taskService);
        ToolResult result = tool.execute(context, Map.of());

        assertEquals(1, ((Map<?, ?>) result.output()).get("total"));
    }

    @Test
    void getTaskReturnsOneTask() {
        when(taskMapper.selectById(77L)).thenReturn(modelTask());

        GetTaskTool tool = new GetTaskTool(taskService);
        ToolResult result = tool.execute(context, Map.of("taskId", 77));

        Map<?, ?> task = (Map<?, ?>) ((Map<?, ?>) result.output()).get("task");
        assertEquals(77L, task.get("taskId"));
        assertEquals("QUEUED", task.get("status"));
    }

    @Test
    void updateTaskChangesStatus() {
        AgentTask existing = modelTask();
        when(taskMapper.selectById(77L)).thenReturn(existing).thenReturn(closedTask());
        when(taskMapper.update(any(), any())).thenReturn(1);

        UpdateTaskTool tool = new UpdateTaskTool(taskService);
        ToolResult result = tool.execute(context, Map.of("taskId", 77, "status", "SUCCEEDED", "note", "done"));

        assertEquals("SUCCEEDED", ((Map<?, ?>) result.output()).get("status"));
    }

    @Test
    void updateClosedTaskIsRejected() {
        AgentTask closed = closedTask();
        closed.setStatus(AgentTaskStatus.SUCCEEDED.name());
        when(taskMapper.selectById(77L)).thenReturn(closed);

        UpdateTaskTool tool = new UpdateTaskTool(taskService);
        assertThrows(com.aipal.common.BizException.class,
                () -> tool.execute(context, Map.of("taskId", 77, "status", "FAILED")));
    }

    @Test
    void childTaskBudgetIsEnforced() {
        when(taskMapper.selectOne(any())).thenReturn(rootTask());
        when(taskMapper.selectCount(any())).thenReturn(5L);

        CreateTaskTool tool = new CreateTaskTool(taskService);
        assertThrows(com.aipal.common.BizException.class,
                () -> tool.execute(context, Map.of("taskType", "REVIEW")));
    }

    private AgentTask rootTask() {
        AgentTask task = new AgentTask();
        task.setId(1L);
        task.setTenantId(1L);
        task.setRunId(99L);
        task.setTaskType("RUN");
        task.setStatus(AgentTaskStatus.RUNNING.name());
        return task;
    }

    private AgentTask modelTask() {
        AgentTask task = new AgentTask();
        task.setId(77L);
        task.setTenantId(1L);
        task.setRunId(99L);
        task.setParentTaskId(1L);
        task.setTaskType("REVIEW");
        task.setStatus(AgentTaskStatus.QUEUED.name());
        return task;
    }

    private AgentTask closedTask() {
        AgentTask task = modelTask();
        task.setStatus(AgentTaskStatus.SUCCEEDED.name());
        return task;
    }
}
