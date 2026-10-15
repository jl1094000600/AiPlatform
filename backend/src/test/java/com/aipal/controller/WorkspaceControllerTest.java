package com.aipal.controller;

import com.aipal.agent.runtime.workspace.Workspace;
import com.aipal.agent.runtime.workspace.WorkspaceManager;
import com.aipal.common.BizException;
import com.aipal.common.Result;
import com.aipal.entity.AgentRun;
import com.aipal.service.AgentRunService;
import com.aipal.service.AgentRunStreamEventService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceControllerTest {

    @Test
    void returnsContractFieldsFromActiveWorkspace() throws Exception {
        Path root = Files.createTempDirectory("ws-contract-");
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src").resolve("App.java"), "class App {}");

        AgentRunService runService = mock(AgentRunService.class);
        WorkspaceManager manager = mock(WorkspaceManager.class);
        AgentRunStreamEventService streamEventService = mock(AgentRunStreamEventService.class);

        AgentRun run = new AgentRun();
        run.setId(9L);
        run.setTenantId(1L);
        run.setStatus("RUNNING");
        when(runService.get(9L)).thenReturn(run);

        Workspace workspace = mock(Workspace.class);
        when(workspace.workDir()).thenReturn(root);
        when(workspace.health()).thenReturn("READY");
        when(manager.find(1L, 9L)).thenReturn(workspace);
        when(streamEventService.listForRun(1L, 9L, null, 700, false))
                .thenReturn(List.of(new AgentRunStreamEventService.RunStreamEventView(
                        1L, 9L, "TOOL_STARTED", 1L, java.util.Map.of("toolName", "fs.read"), true,
                        java.time.LocalDateTime.now())));

        WorkspaceController controller = new WorkspaceController(runService, manager, streamEventService);
        Result<WorkspaceController.WorkspaceView> result = controller.getWorkspace(9L);

        WorkspaceController.WorkspaceView view = result.getData();
        assertEquals("READY", view.status());
        assertEquals(root.toAbsolutePath().normalize().toString(), view.workDir());
        assertEquals(2, view.files().size());
        assertTrue(view.files().stream().anyMatch(f -> f.path().equals("pom.xml") && f.size() > 0));
        assertTrue(view.files().stream().anyMatch(f -> f.path().equals("src/App.java")));
        assertTrue(view.recentLogs().contains("TOOL_STARTED"));
        verify(runService).get(9L);
    }

    @Test
    void placeholderIsStableWhenNoWorkspaceBound() {
        AgentRunService runService = mock(AgentRunService.class);
        WorkspaceManager manager = mock(WorkspaceManager.class);
        AgentRunStreamEventService streamEventService = mock(AgentRunStreamEventService.class);

        AgentRun run = new AgentRun();
        run.setId(9L);
        run.setTenantId(1L);
        run.setStatus("SUCCEEDED");
        when(runService.get(9L)).thenReturn(run);
        when(manager.find(1L, 9L)).thenReturn(null);
        when(streamEventService.listForRun(1L, 9L, null, 700, false)).thenReturn(List.of());

        WorkspaceController controller = new WorkspaceController(runService, manager, streamEventService);
        WorkspaceController.WorkspaceView view = controller.getWorkspace(9L).getData();

        assertEquals("STOPPED", view.status());
        assertNull(view.workDir());
        assertTrue(view.files().isEmpty());
        assertEquals("", view.recentLogs());
    }

    @Test
    void propagatesForbiddenFromRunAccessCheck() {
        AgentRunService runService = mock(AgentRunService.class);
        when(runService.get(9L)).thenThrow(new BizException(403, "无权限访问"));
        WorkspaceController controller = new WorkspaceController(runService,
                mock(WorkspaceManager.class), mock(AgentRunStreamEventService.class));

        BizException ex = assertThrows(BizException.class, () -> controller.getWorkspace(9L));
        assertEquals(403, ex.getCode());
    }
}
