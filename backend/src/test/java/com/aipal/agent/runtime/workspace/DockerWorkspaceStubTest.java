package com.aipal.agent.runtime.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SC01 A02/A03/A04: fake docker binary stub (no daemon required).
 * Captures real startup argv to prove isolation flags, host-socket absence and
 * container reclaim on close; proves fail-closed refusal when docker cannot start.
 */
class DockerWorkspaceStubTest {

    private static final String CONTAINER_ID = "fakecid001";

    @TempDir
    Path temp;

    @Test
    void startUsesIsolationArgsWithoutHostSocketOrPrivilege() throws Exception {
        Path argsLog = temp.resolve("docker-args.log");
        Path stub = writeStub(argsLog, false);
        Path workDir = temp.resolve("ws");
        Files.createDirectories(workDir);

        DockerWorkspace workspace = new DockerWorkspace(workDir, stub.toString(), "maven:3.9-eclipse-temurin-21");
        workspace.start();
        try {
            String args = Files.readString(argsLog);
            assertTrue(args.contains("run"), "docker run must be invoked: " + args);
            assertTrue(args.contains("--network none"), "container must run with no network: " + args);
            assertTrue(args.contains("--memory 512m"), "container must have a memory limit: " + args);
            assertTrue(args.contains("--cpus"), "container must have a cpu limit: " + args);
            assertTrue(args.contains("--pids-limit"), "container must have a pids limit: " + args);
            assertFalse(args.contains("docker.sock"), "host docker socket must not be mounted: " + args);
            assertFalse(args.contains("--privileged"), "container must not be privileged: " + args);
            assertFalse(args.contains("/:/"), "host root must not be mounted: " + args);
        } finally {
            workspace.close();
        }
    }

    @Test
    void closeForceRemovesContainerForTerminalReclaim() throws Exception {
        Path argsLog = temp.resolve("docker-args.log");
        Path stub = writeStub(argsLog, false);
        Path workDir = temp.resolve("ws-close");
        Files.createDirectories(workDir);

        DockerWorkspace workspace = new DockerWorkspace(workDir, stub.toString(), "img");
        workspace.start();
        workspace.close();
        workspace.close(); // idempotent

        String args = Files.readString(argsLog);
        assertTrue(args.contains("rm -f " + CONTAINER_ID),
                "close must force-remove the container exactly: " + args);
    }

    @Test
    void startFailureRefusesHostFallbackInFailClosedMode() throws Exception {
        Path argsLog = temp.resolve("docker-args.log");
        Path stub = writeStub(argsLog, true);
        Path workDir = temp.resolve("ws-fail");

        WorkspaceFactory factory = new WorkspaceFactory("production", stub.toString(), "img");
        WorkspaceFactory.WorkspaceUnavailableException error = assertThrows(
                WorkspaceFactory.WorkspaceUnavailableException.class,
                () -> factory.open(workDir),
                "docker start failure must not fall back to a host workspace");
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("refusing host fallback"),
                "error must state host fallback is refused: " + error.getMessage());
    }

    @Test
    void dockerModeUnavailableIsRejectedNotLocalFallback() {
        WorkspaceFactory factory = new WorkspaceFactory(
                "docker", "definitely-not-a-docker-binary-xyz", "img");
        WorkspaceFactory.WorkspaceUnavailableException error = assertThrows(
                WorkspaceFactory.WorkspaceUnavailableException.class,
                () -> factory.open(temp.resolve("ws-missing")),
                "mode=docker must not silently degrade to LocalWorkspace");
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("refusing host fallback"),
                "error must state host fallback is refused: " + error.getMessage());
    }

    @Test
    void execInsideStartedWorkspaceReturnsStubOutput() throws Exception {
        Path argsLog = temp.resolve("docker-args.log");
        Path stub = writeStub(argsLog, false);
        Path workDir = temp.resolve("ws-exec");
        Files.createDirectories(workDir);

        DockerWorkspace workspace = new DockerWorkspace(workDir, stub.toString(), "img");
        workspace.start();
        try {
            CommandResult result = workspace.exec(List.of("cmd.exe", "/c", "echo hello"), Duration.ofSeconds(10));
            assertTrue(result.succeeded(), "stub exec should succeed: " + result.summary());
        } finally {
            workspace.close();
        }
    }

    /**
     * Writes a platform stub "docker" binary that logs every invocation and
     * simulates version/run/rm/exec/inspect. When {@code failRun} is true the
     * {@code run} subcommand fails (A02 container start failure).
     */
    private Path writeStub(Path argsLog, boolean failRun) throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            Path stub = temp.resolve("fake-docker.cmd");
            String runAction = failRun
                    ? "if \"%1\"==\"run\" (\n  echo failed to start container 1>&2\n  exit /b 1\n)\n"
                    : "if \"%1\"==\"run\" (\n  echo " + CONTAINER_ID + "\n  exit /b 0\n)\n";
            String body = "@echo off\r\n"
                    + "echo ARGS:%*>>\"" + argsLog.toAbsolutePath() + "\"\r\n"
                    + "if \"%1\"==\"version\" (\r\n  echo 24.0.0\r\n  exit /b 0\r\n)\r\n"
                    + runAction.replace("\n", "\r\n")
                    + "if \"%1\"==\"rm\" (\r\n  exit /b 0\r\n)\r\n"
                    + "if \"%1\"==\"exec\" (\r\n  echo stub-exec-ok\r\n  exit /b 0\r\n)\r\n"
                    + "if \"%1\"==\"inspect\" (\r\n  echo true\r\n  exit /b 0\r\n)\r\n"
                    + "exit /b 0\r\n";
            Files.writeString(stub, body);
            return stub;
        }
        Path stub = temp.resolve("fake-docker.sh");
        String runAction = failRun
                ? "  run) echo \"failed to start container\" 1>&2; exit 1 ;;\n"
                : "  run) echo \"" + CONTAINER_ID + "\"; exit 0 ;;\n";
        String body = "#!/bin/sh\n"
                + "echo \"ARGS:$*\" >> \"" + argsLog.toAbsolutePath() + "\"\n"
                + "case \"$1\" in\n"
                + "  version) echo \"24.0.0\"; exit 0 ;;\n"
                + runAction
                + "  rm) exit 0 ;;\n"
                + "  exec) echo \"stub-exec-ok\"; exit 0 ;;\n"
                + "  inspect) echo \"true\"; exit 0 ;;\n"
                + "esac\n"
                + "exit 0\n";
        Files.writeString(stub, body);
        assertTrue(stub.toFile().setExecutable(true), "stub must be executable");
        return stub;
    }
}
