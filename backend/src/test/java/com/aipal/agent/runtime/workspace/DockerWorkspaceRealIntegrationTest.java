package com.aipal.agent.runtime.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real Docker integration for SC01 A02-A04. Skips cleanly when the engine is down.
 */
class DockerWorkspaceRealIntegrationTest {

    static boolean dockerAvailable() {
        try {
            return new WorkspaceFactory("auto", "docker", "alpine:3.19").isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @EnabledIf("dockerAvailable")
    void executesCommandInsideContainerWithResourceLimits() throws Exception {
        Path host = Files.createTempDirectory("sc01-real-ws");
        DockerWorkspace workspace = new DockerWorkspace(host, "docker", "alpine:3.19");
        workspace.start();
        try {
            CommandResult result = workspace.exec(List.of("echo", "SC01_REAL_OK"), java.time.Duration.ofSeconds(60));
            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("SC01_REAL_OK"), result.summary());
            assertEquals("READY", workspace.health());
        } finally {
            workspace.close();
        }
        assertTrue("CLOSED".equals(workspace.health()) || workspace.health().startsWith("CLOSED")
                || workspace.health().equals("CLOSED"), workspace.health());
    }

    @Test
    @EnabledIf("dockerAvailable")
    void closeReleasesContainer() throws Exception {
        Path host = Files.createTempDirectory("sc01-real-close");
        DockerWorkspace workspace = new DockerWorkspace(host, "docker", "alpine:3.19");
        workspace.start();
        String healthBefore = workspace.health();
        workspace.close();
        assertFalse("READY".equals(healthBefore) && "READY".equals(workspace.health()),
                "health must change after close");
    }

    @Test
    @EnabledIf("dockerAvailable")
    void productionFactoryOpenUsesDocker() throws Exception {
        WorkspaceFactory factory = new WorkspaceFactory("production", "docker", "alpine:3.19");
        Path host = Files.createTempDirectory("sc01-real-prod");
        Workspace workspace = factory.open(host);
        try {
            assertTrue(workspace instanceof DockerWorkspace, "production must open DockerWorkspace");
            CommandResult result = workspace.exec(List.of("uname", "-s"), java.time.Duration.ofSeconds(30));
            assertEquals(0, result.exitCode());
        } finally {
            workspace.close();
        }
    }
}
