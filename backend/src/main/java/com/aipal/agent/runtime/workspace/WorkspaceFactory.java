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
 * Falls back to {@link LocalWorkspace} (with a warning) when Docker is unavailable so
 * compilation and local development never depend on a Docker daemon.
 */
@Component
@Slf4j
public class WorkspaceFactory {

    public static final String MODE_AUTO = "auto";
    public static final String MODE_LOCAL = "local";
    public static final String MODE_DOCKER = "docker";

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

    /** Create a workspace rooted at {@code root}; Docker is used when selected and available. */
    public Workspace open(Path root) {
        Path workRoot = root == null ? defaultRoot() : root.toAbsolutePath().normalize();
        if (MODE_LOCAL.equals(mode)) {
            return LocalWorkspace.wrap(workRoot);
        }
        boolean dockerPreferred = MODE_DOCKER.equals(mode) || MODE_AUTO.equals(mode);
        if (dockerPreferred) {
            if (isDockerAvailable()) {
                try {
                    DockerWorkspace dockerWorkspace = new DockerWorkspace(workRoot, dockerBinary, dockerImage);
                    dockerWorkspace.start();
                    return dockerWorkspace;
                } catch (Exception e) {
                    log.warn("Docker workspace failed to start, falling back to LocalWorkspace: {}", e.getMessage());
                }
            } else if (MODE_DOCKER.equals(mode)) {
                log.warn("Docker is not available on this host; falling back to LocalWorkspace");
            }
        }
        return LocalWorkspace.wrap(workRoot);
    }

    /** Ephemeral temp-dir workspace (tools). */
    public Workspace openLocalTemp() {
        try {
            return LocalWorkspace.createTemp();
        } catch (IOException e) {
            throw new IllegalStateException("failed to create local workspace: " + e.getMessage(), e);
        }
    }

    /** Wrap an existing directory without Docker (artifact roots, test execution). */
    public Workspace openLocal(Path root) {
        return LocalWorkspace.wrap(root);
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
}
