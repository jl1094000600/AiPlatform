package com.aipal.agent.runtime.workspace;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Workspace backed by a local directory (temp dir or an existing artifact root).
 * Suitable for development and hosts without Docker.
 */
public class LocalWorkspace implements Workspace {

    private static final int MAX_LOG_CHARS = 64_000;

    private final Path root;
    private final boolean ephemeral;
    private final PosixWorkspaceFiles files;
    private volatile boolean closed;

    public LocalWorkspace(Path root) {
        this(root, false);
    }

    private LocalWorkspace(Path root, boolean ephemeral) {
        this.root = root.toAbsolutePath().normalize();
        this.ephemeral = ephemeral;
        try {
            this.files = new PosixWorkspaceFiles(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot initialize secure workspace", e);
        }
    }

    public static LocalWorkspace createTemp() throws IOException {
        Path dir = Files.createTempDirectory("aipal-ws-");
        return wrap(dir);
    }

    public static LocalWorkspace createTemp(Path parent) throws IOException {
        Files.createDirectories(parent);
        Path dir = Files.createTempDirectory(parent, "ws-");
        return new LocalWorkspace(dir, true);
    }

    public static LocalWorkspace wrap(Path root) {
        return new LocalWorkspace(root);
    }

    @Override
    public Path workDir() {
        return root;
    }

    @Override
    public CommandResult exec(List<String> command, Duration timeout) {
        ensureOpen();
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command is required");
        }
        ShellCommandGuard.requireSafeCommand(WorkspacePaths.displayCommand(command));
        Duration effective = timeout == null ? Duration.ofSeconds(60) : timeout;
        Instant start = Instant.now();
        String display = WorkspacePaths.displayCommand(command);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(root.toFile());
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            return CommandResult.failure(display, -1, "", e.getMessage(), Duration.between(start, Instant.now()));
        }
        CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        try {
            boolean finished = process.waitFor(Math.max(1, effective.toMillis()), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                String stdout = awaitQuietly(stdoutFuture);
                String stderr = awaitQuietly(stderrFuture);
                return CommandResult.timedOut(display, stdout, stderr, Duration.between(start, Instant.now()));
            }
            int exit = process.exitValue();
            String stdout = awaitQuietly(stdoutFuture);
            String stderr = awaitQuietly(stderrFuture);
            Duration duration = Duration.between(start, Instant.now());
            return exit == 0
                    ? new CommandResult(0, stdout, stderr, false, duration, display)
                    : CommandResult.failure(display, exit, stdout, stderr, duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return CommandResult.timedOut(display, "", "interrupted", Duration.between(start, Instant.now()));
        }
    }

    @Override
    public void putFile(String relPath, String content) throws IOException {
        ensureOpen();
        try (PosixWorkspaceFiles.Directory parent = files.openParent(relPath, true)) {
            parent.write(files.fileName(relPath), content == null ? "" : content);
        }
    }

    @Override
    public String getFile(String relPath) throws IOException {
        ensureOpen();
        try (PosixWorkspaceFiles.Directory parent = files.openParent(relPath, false)) {
            return parent.read(files.fileName(relPath));
        }
    }

    @Override
    public String health() {
        if (closed) {
            return "CLOSED";
        }
        return Files.isDirectory(root) ? "READY" : "MISSING_ROOT";
    }

    @Override
    public void close() throws IOException {
        closed = true;
        if (ephemeral) {
            deleteRecursively(root);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("workspace is closed");
        }
    }

    private static String readStream(java.io.InputStream in) {
        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (log.length() < MAX_LOG_CHARS) {
                    log.append(line).append('\n');
                }
            }
        } catch (IOException e) {
            log.append("stream read error: ").append(e.getMessage());
        }
        return log.toString();
    }

    private static String awaitQuietly(CompletableFuture<String> future) {
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "";
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        }
    }
}
