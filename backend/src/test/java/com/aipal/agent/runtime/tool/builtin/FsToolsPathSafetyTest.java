package com.aipal.agent.runtime.tool.builtin;

import com.aipal.agent.runtime.tool.ToolInvocationContext;
import com.aipal.agent.runtime.tool.ToolResult;
import com.aipal.agent.runtime.workspace.LocalWorkspace;
import com.aipal.agent.runtime.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FsToolsPathSafetyTest {

    @TempDir
    Path root;

    @Test
    void fsWriteRejectsParentTraversal() {
        WorkspaceManager manager = mock(WorkspaceManager.class);
        when(manager.acquire(any(ToolInvocationContext.class))).thenReturn(LocalWorkspace.wrap(root));
        FsWriteTool tool = new FsWriteTool(manager);

        assertThrows(IllegalArgumentException.class, () -> tool.execute(context(),
                Map.of("path", "../escape.txt", "content", "x")));
        assertThrows(IllegalArgumentException.class, () -> tool.execute(context(),
                Map.of("path", "a/../../escape.txt", "content", "x")));
        assertThrows(IllegalArgumentException.class, () -> tool.execute(context(),
                Map.of("path", "C:/Windows/evil.txt", "content", "x")));
    }

    @Test
    void fsWriteRejectsAbsolutePath() {
        WorkspaceManager manager = mock(WorkspaceManager.class);
        when(manager.acquire(any(ToolInvocationContext.class))).thenReturn(LocalWorkspace.wrap(root));
        FsWriteTool tool = new FsWriteTool(manager);

        assertThrows(IllegalArgumentException.class, () -> tool.execute(context(),
                Map.of("path", "/etc/passwd", "content", "x")));
    }

    @Test
    void fsReadRejectsParentTraversal() throws Exception {
        WorkspaceManager manager = mock(WorkspaceManager.class);
        when(manager.acquire(any(ToolInvocationContext.class))).thenReturn(LocalWorkspace.wrap(root));
        FsReadTool tool = new FsReadTool(manager);

        assertThrows(IllegalArgumentException.class, () -> tool.execute(context(),
                Map.of("path", "../secret.txt")));
    }

    @Test
    void fsWriteThenFsReadRoundTrip() throws Exception {
        WorkspaceManager manager = mock(WorkspaceManager.class);
        when(manager.acquire(any(ToolInvocationContext.class))).thenReturn(LocalWorkspace.wrap(root));
        FsWriteTool writeTool = new FsWriteTool(manager);
        FsReadTool readTool = new FsReadTool(manager);

        ToolResult writeResult = writeTool.execute(context(), Map.of("path", "src/App.java", "content", "class App {}"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) writeResult.output()).get("written"));

        ToolResult readResult = readTool.execute(context(), Map.of("path", "src/App.java"));
        Map<?, ?> output = (Map<?, ?>) readResult.output();
        assertEquals("class App {}", output.get("content"));
        assertTrue(readResult.outputSummary().contains("src/App.java"));
    }

    private ToolInvocationContext context() {
        return new ToolInvocationContext(1L, 7L, 11L, 22L, 88L, 33L, "project-a",
                "trace-a", "call-1", "fs.write", 55L, 66L, 77L);
    }
}
