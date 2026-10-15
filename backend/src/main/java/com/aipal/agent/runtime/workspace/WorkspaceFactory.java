package com.aipal.agent.runtime.workspace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Chooses a {@link Workspace} implementation from configuration.
 *
 * <p>SC01: production and delivery profiles refuse host fallback. Local workspaces are
 * only available via explicit trusted configuration ({@code aipal.workspace.mode=local});
 * HTTP/API requests must not switch execution profile at runtime.
 */
@Component
@Slf4j
public class WorkspaceFactory {

    public static final String MODE_AUTO = "auto";
    public static final String MODE_LOCAL = "local";
    public static final String MODE_DOCKER = "docker";
    /** Production/delivery: mandatory container execution, fail-closed. */
    public static final String MODE_PRODUCTION = "production";
    public static final String MODE_DELIVERY = "delivery";

    private final String mode;
    private final String dockerBinary;
    private final String dockerImage;

    public WorkspaceFactory(
            @Value("${aipal.workspace.mode:auto}") String mode,
            @Value("${aipal.workspace.docker-binary:docker}") String dockerBinary,
            @Value("${aipal.workspace.docker-image:maven:3.9-eclipse-temurin-21}") String dockerImage) {
        this.mode = mode == null ? MODE_AUTO : mode.trim().toLowerCase(Locale.ROOT);
        this.dockerBinary = dockerBinary;
        this.dockerImage = dockerImage;
    }

    /** Create a workspace rooted at {@code root}. Fail-closed profiles never host-fallback. */
    public Workspace open(Path root) {
        Path workRoot = root == null ? defaultRoot() : root.toAbsolutePath().normalize();
        if (MODE_LOCAL.equals(mode)) {
            return LocalWorkspace.wrap(workRoot);
        }
        if (isFailClosedMode()) {
            return openDockerOrThrow(workRoot, "mode=" + mode + " requires container isolation");
        }
        // auto / docker (dev-oriented): prefer docker, allow local only for non-trusted dev
        if (MODE_DOCKER.equals(mode)) {
            return openDockerOrThrow(workRoot, "mode=docker requires container isolation");
        }
        if (isDockerAvailable()) {
            try {
                DockerWorkspace dockerWorkspace = new DockerWorkspace(workRoot, dockerBinary, dockerImage);
                dockerWorkspace.start();
                return dockerWorkspace;
            } catch (Exception e) {
                log.warn("Docker workspace failed to start in auto mode, using local dev workspace: {}", e.getMessage());
            }
        } else {
            log.warn("Docker is not available; auto mode using local dev workspace");
        }
        return LocalWorkspace.wrap(workRoot);
    }

    /**
     * Ephemeral workspace for tools. In fail-closed modes this is always container-backed.
     */
    public Workspace openToolWorkspace() {
        if (isFailClosedMode() || MODE_DOCKER.equals(mode)) {
            return open(null);
        }
        if (MODE_LOCAL.equals(mode)) {
            return openLocalTemp();
        }
        try {
            return LocalWorkspace.createTemp();
        } catch (IOException e) {
            throw new IllegalStateException("failed to create local workspace: " + e.getMessage(), e);
        }
    }

    /** Ephemeral temp-dir workspace (explicit local/dev only). */
    public Workspace openLocalTemp() {
        if (isFailClosedMode()) {
            throw new IllegalStateException(
                    "LocalWorkspace is not allowed in mode=" + mode + " (fail-closed execution profile)");
        }
        try {
            return LocalWorkspace.createTemp();
        } catch (IOException e) {
            throw new IllegalStateException("failed to create local workspace: " + e.getMessage(), e);
        }
    }

    /** Wrap an existing directory without Docker. Blocked in fail-closed modes. */
    public Workspace openLocal(Path root) {
        if (isFailClosedMode()) {
            throw new IllegalStateException(
                    "openLocal is not allowed in mode=" + mode + " (fail-closed execution profile)");
        }
        return LocalWorkspace.wrap(root);
    }

    private Workspace openDockerOrThrow(Path workRoot, String reason) {
        if (!isDockerAvailable()) {
            throw new WorkspaceUnavailableException(
                    "Docker engine unavailable; refusing host fallback (" + reason + ")");
        }
        try {
            DockerWorkspace dockerWorkspace = new DockerWorkspace(workRoot, dockerBinary, dockerImage);
            dockerWorkspace.start();
            return dockerWorkspace;
        } catch (Exception e) {
            throw new WorkspaceUnavailableException(
                    "Docker workspace failed to start; refusing host fallback (" + reason + "): " + e.getMessage(), e);
        }
    }

    public boolean isFailClosedMode() {
        return MODE_PRODUCTION.equals(mode) || MODE_DELIVERY.equals(mode);
    }

    public boolean isDockerAvailable() {
        try {
            CommandResult result = DockerWorkspace.runHostCommand(
                    List.of(dockerBinary, "version", "--format", "{{.Server.Version}}"),
                    Duration.ofSeconds(8));
            return result.succeeded();
        } catch (Exception e) {
            return false;
        }
    }

    public String mode() {
        return mode;
    }

    private Path defaultRoot() {
        return Paths.get(System.getProperty("java.io.tmpdir"), "aipal-workspace").toAbsolutePath().normalize();
    }

    /** Fail-closed execution profile cannot obtain a workspace. */
    public static class WorkspaceUnavailableException extends IllegalStateException {
        public WorkspaceUnavailableException(String message) {
            super(message);
        }

        public WorkspaceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
