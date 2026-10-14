package com.aipal.agent.runtime.workspace;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Workspace isolated in a disposable Docker container (non-privileged, workspace volume only).
 * Uses the docker CLI via {@link ProcessBuilder}; no extra dependencies.
 */
@Slf4j
public class DockerWorkspace implements Workspace {

    private static final int MAX_LOG_CHARS = 64_000;

    private final Path hostWorkDir;
    private final String dockerBinary;
    private final String image;
    private final String containerName;
    private String containerId;
    private volatile boolean closed;

    public DockerWorkspace(Path hostWorkDir, String dockerBinary, String image) {
        this.hostWorkDir = hostWorkDir.toAbsolutePath().normalize();
        this.dockerBinary = dockerBinary == null || dockerBinary.isBlank() ? "docker" : dockerBinary;
        this.image = image == null || image.isBlank() ? "maven:3.9-eclipse-temurin-21" : image;
        this.containerName = "aipal-ws-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** Boot the container. Caller must have verified Docker is available. */
    public void start() throws IOException {
        Files.createDirectories(hostWorkDir);
        List<String> cmd = new ArrayList<>(List.of(
                dockerBinary, "run", "-d", "--rm",
                "--name", containerName,
                "--network", "none",
                "--memory", "512m",
                "--cpus", "1",
                "--pids-limit", "256",
                "-v", hostWorkDir + ":/workspace",
                "-w", "/workspace",
                image,
                "sleep", "3600"
        ));
        CommandResult result = runHostCommand(cmd, Duration.ofSeconds(120));
        if (!result.succeeded()) {
            throw new IOException("failed to start docker workspace: " + result.summary());
        }
        containerId = result.stdout().strip();
    }

    @Override
    public Path workDir() {
        return hostWorkDir;
    }

    @Override
    public CommandResult exec(List<String> command, Duration timeout) {
        ensureOpen();
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command is required");
        }
        ShellCommandGuard.requireSafeCommand(WorkspacePaths.displayCommand(command));
        if (containerId == null || containerId.isBlank()) {
            throw new IllegalStateException("docker workspace is not started");
        }
        Duration effective = timeout == null ? Duration.ofSeconds(60) : timeout;
        List<String> cmd = new ArrayList<>();
        cmd.add(dockerBinary);
        cmd.add("exec");
        cmd.add(containerId);
        cmd.addAll(command);
        Instant start = Instant.now();
        CommandResult result = runHostCommand(cmd, effective);
        return new CommandResult(result.exitCode(), result.stdout(), result.stderr(),
                result.timedOut(), Duration.between(start, Instant.now()),
                WorkspacePaths.displayCommand(command));
    }

    @Override
    public void putFile(String relPath, String content) throws IOException {
        // Host mount is the source of truth; write through LocalWorkspace for path checks.
        LocalWorkspace.wrap(hostWorkDir).putFile(relPath, content);
    }

    @Override
    public String getFile(String relPath) throws IOException {
        return LocalWorkspace.wrap(hostWorkDir).getFile(relPath);
    }

    @Override
    public String health() {
        if (closed) {
            return "CLOSED";
        }
        if (containerId == null) {
            return "NOT_STARTED";
        }
        CommandResult result = runHostCommand(List.of(dockerBinary, "inspect", "-f", "{{.State.Running}}", containerId),
                Duration.ofSeconds(10));
        return result.succeeded() && "true".equalsIgnoreCase(result.stdout().strip()) ? "READY" : "UNHEALTHY";
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (containerId != null && !containerId.isBlank()) {
            runHostCommand(List.of(dockerBinary, "rm", "-f", containerId), Duration.ofSeconds(30));
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("workspace is closed");
        }
    }

    static CommandResult runHostCommand(List<String> command, Duration timeout) {
        String display = WorkspacePaths.displayCommand(command);
        Instant start = Instant.now();
        ProcessBuilder builder = new ProcessBuilder(command);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            return CommandResult.failure(display, -1, "", e.getMessage(), Duration.between(start, Instant.now()));
        }
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread outReader = Thread.ofVirtual().name("docker-ws-out").start(() -> drain(process, stdout, false));
        Thread errReader = Thread.ofVirtual().name("docker-ws-err").start(() -> drain(process, stderr, true));
        try {
            boolean finished = process.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                joinQuietly(outReader);
                joinQuietly(errReader);
                return CommandResult.timedOut(display, stdout.toString(), stderr.toString(),
                        Duration.between(start, Instant.now()));
            }
            joinQuietly(outReader);
            joinQuietly(errReader);
            int exit = process.exitValue();
            Duration duration = Duration.between(start, Instant.now());
            return exit == 0
                    ? new CommandResult(0, stdout.toString(), stderr.toString(), false, duration, display)
                    : CommandResult.failure(display, exit, stdout.toString(), stderr.toString(), duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return CommandResult.timedOut(display, stdout.toString(), "interrupted",
                    Duration.between(start, Instant.now()));
        }
    }

    private static void drain(Process process, StringBuilder sink, boolean err) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                err ? process.getErrorStream() : process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (sink) {
                    if (sink.length() < MAX_LOG_CHARS) {
                        sink.append(line).append('\n');
                    }
                }
            }
        } catch (IOException ignored) {
            // process ended
        }
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
