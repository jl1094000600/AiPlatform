package com.aipal.agent.runtime.workspace;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SC01 A04: workspace bindings are tenant/run scoped and released (closed) on terminal reclaim.
 */
class WorkspaceManagerTest {

    @Test
    void workspaceBindingsAreIsolatedPerTenant() {
        WorkspaceFactory factory = new WorkspaceFactory("local", "docker", "img");
        WorkspaceManager manager = new WorkspaceManager(factory);

        String tenantOne = manager.key(1L, 99L);
        String tenantTwo = manager.key(2L, 99L);
        manager.bind(tenantOne, new TrackingWorkspace());

        assertNotNull(manager.find(tenantOne), "owning tenant must see its workspace");
        assertNull(manager.find(tenantTwo), "other tenant must not see the binding");
        assertTrue(manager.isActive(tenantOne));
        assertFalse(manager.isActive(tenantTwo));
    }

    @Test
    void releaseClosesWorkspaceExactlyOnce() {
        WorkspaceFactory factory = new WorkspaceFactory("local", "docker", "img");
        WorkspaceManager manager = new WorkspaceManager(factory);
        TrackingWorkspace workspace = new TrackingWorkspace();
        String key = manager.key(1L, 42L);
        manager.bind(key, workspace);

        manager.release(1L, 42L);
        manager.release(1L, 42L); // second release is a no-op

        assertTrue(workspace.closed, "release must close the workspace (resource reclaim)");
        assertNull(manager.find(key), "binding must be dropped after release");
        assertFalse(manager.isActive(key));
    }

    private static final class TrackingWorkspace implements Workspace {
        volatile boolean closed;

        @Override
        public Path workDir() {
            return Paths.get("target");
        }

        @Override
        public CommandResult exec(List<String> command, Duration timeout) {
            return CommandResult.success("noop", "", Duration.ZERO);
        }

        @Override
        public void putFile(String relPath, String content) {
        }

        @Override
        public String getFile(String relPath) {
            return "";
        }

        @Override
        public String health() {
            return closed ? "CLOSED" : "READY";
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
