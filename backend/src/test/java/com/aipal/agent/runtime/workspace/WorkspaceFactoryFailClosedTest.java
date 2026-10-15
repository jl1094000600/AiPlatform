package com.aipal.agent.runtime.workspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SC01: production/delivery profiles must refuse host fallback.
 */
class WorkspaceFactoryFailClosedTest {

    @Test
    void productionModeRejectsOpenLocal() {
        WorkspaceFactory factory = new WorkspaceFactory("production", "docker", "test-image");
        assertTrue(factory.isFailClosedMode());
        assertThrows(IllegalStateException.class,
                () -> factory.openLocal(java.nio.file.Paths.get("target")),
                "openLocal must be rejected in production");
    }

    @Test
    void productionModeRejectsOpenLocalTemp() {
        WorkspaceFactory factory = new WorkspaceFactory("delivery", "docker", "test-image");
        assertThrows(IllegalStateException.class, factory::openLocalTemp,
                "openLocalTemp must be rejected in delivery");
    }

    @Test
    void localModeAllowsExplicitLocalWorkspace() {
        WorkspaceFactory factory = new WorkspaceFactory("local", "docker", "test-image");
        assertFalse(factory.isFailClosedMode());
        Workspace workspace = factory.openLocal(java.nio.file.Paths.get("target"));
        try {
            assertTrue(workspace.workDir() != null);
        } finally {
            try {
                workspace.close();
            } catch (Exception ignored) {
                // test cleanup
            }
        }
    }

    @Test
    void productionModeOpenFailsClosedWhenDockerUnavailable() {
        WorkspaceFactory factory = new WorkspaceFactory("production", "docker-not-installed-binary-xyz", "test-image");
        assertThrows(WorkspaceFactory.WorkspaceUnavailableException.class,
                () -> factory.open(null),
                "open must fail closed when docker is unavailable");
    }
}
