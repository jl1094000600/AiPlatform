package com.aipal.agent.runtime.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalWorkspaceTest {

    @TempDir
    Path root;

    @Test
    void putAndGetFileInsideWorkspace() throws Exception {
        LocalWorkspace workspace = LocalWorkspace.wrap(root);
        workspace.putFile("hello/world.txt", "hi there");
        assertEquals("hi there", workspace.getFile("hello/world.txt"));
        assertTrue(Files.isRegularFile(root.resolve("hello/world.txt")));
    }

    @Test
    void rejectsPathEscapeOnPutAndGet() {
        LocalWorkspace workspace = LocalWorkspace.wrap(root);
        assertThrows(IllegalArgumentException.class, () -> workspace.putFile("../evil.txt", "x"));
        assertThrows(IllegalArgumentException.class, () -> workspace.getFile("../evil.txt"));
        assertThrows(IllegalArgumentException.class, () -> workspace.putFile("/tmp/evil.txt", "x"));
    }

    @Test
    void executesCommandAndCapturesStdout() {
        LocalWorkspace workspace = LocalWorkspace.wrap(root);
        boolean windows = WorkspacePaths.isWindows();
        List<String> command = windows
                ? List.of("cmd.exe", "/c", "echo hello-workspace")
                : List.of("sh", "-c", "echo hello-workspace");
        CommandResult result = workspace.exec(command, Duration.ofSeconds(10));
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("hello-workspace"));
        assertTrue(result.succeeded());
    }

    @Test
    void rejectsDangerousCommand() {
        LocalWorkspace workspace = LocalWorkspace.wrap(root);
        assertThrows(IllegalArgumentException.class,
                () -> workspace.exec(List.of("rm", "-rf", "/"), Duration.ofSeconds(5)));
    }

    @Test
    void healthReportsReady() throws Exception {
        LocalWorkspace workspace = LocalWorkspace.createTemp();
        assertEquals("READY", workspace.health());
        workspace.close();
        assertEquals("CLOSED", workspace.health());
    }
}
