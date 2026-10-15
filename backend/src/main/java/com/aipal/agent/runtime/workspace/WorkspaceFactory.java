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
 * <p>SC01/F01: workspaces are rooted per (tenant, project/run, batch) identity so tenants
 * never share a host directory. F05: production/delivery/docker refuse host fallback;
 * local is explicit trusted dev only. API requests must not switch execution profile.
 */
@Component
@Slf4j
public class WorkspaceFactory {

    public static final String MODE_AUTO = "auto";
    public static final String MODE_LOCAL = "local";
    public static final String MODE_DOCKER = "docker";
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
        if (isFailClosedMode() || MODE_DOCKER.equals(mode)) {
            return openDockerOrThrow(workRoot, "mode=" + mode + " requires container isolation");
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
     * Tool workspace bound to server-side identity (F01). Identity parts are required;
     * {@code 0} placeholders are rejected so tenants cannot share default roots.
     */
    public Workspace openToolWorkspace(WorkspaceIdentity identity) {
        if (identity == null || !identity.isolated()) {
            throw new IllegalArgumentException(
                    "Workspace identity (tenantId/runId or projectId+batch) is required; refusing anonymous shared root");
        }
        Path workRoot = identity.resolveRoot();
        if (MODE_LOCAL.equals(mode)) {
            return LocalWorkspace.wrap(workRoot);
        }
        if (isFailClosedMode() || MODE_DOCKER.equals(mode)) {
            return openDockerOrThrow(workRoot, "mode=" + mode + " tool workspace");
        }
        try {
            return LocalWorkspace.createTemp(workRoot);
        } catch (IOException e) {
            throw new IllegalStateException("failed to create local workspace: " + e.getMessage(), e);
        }
    }

    /** Legacy entry used only by explicit local dev; never anonymous in fail-closed modes. */
    public Workspace openLocalTemp() {
        if (isFailClosedMode() || MODE_DOCKER.equals(mode)) {
            throw new IllegalStateException(
                    "LocalWorkspace is not allowed in mode=" + mode + " (fail-closed execution profile)");
        }
        try {
            return LocalWorkspace.createTemp();
        } catch (IOException e) {
            throw new IllegalStateException("failed to create local workspace: " + e.getMessage(), e);
        }
    }

    /** Wrap an existing directory without Docker. Rejected in any container-enforcing mode. */
    public Workspace openLocal(Path root) {
        if (isFailClosedMode() || MODE_DOCKER.equals(mode)) {
            throw new IllegalStateException(
                    "openLocal is not allowed in mode=" + mode + " (container-enforcing profile)");
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

    public boolean isContainerEnforcingMode() {
        return isFailClosedMode() || MODE_DOCKER.equals(mode);
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

    /**
     * Server-side workspace identity. All parts that separate tenants/runs/batches must be real.
     */
    public record WorkspaceIdentity(Long tenantId, Long projectId, Long runId, Long batchId, Path explicitRoot) {

        public boolean isolated() {
            if (explicitRoot != null) {
                return true;
            }
            if (tenantId == null || tenantId <= 0) {
                return false;
            }
            boolean hasRun = runId != null && runId > 0;
            boolean hasBatch = batchId != null && batchId > 0;
            return hasRun || hasBatch;
        }

        public Path resolveRoot() {
            if (explicitRoot != null) {
                return explicitRoot.toAbsolutePath().normalize();
            }
            StringBuilder sb = new StringBuilder();
            sb.append("t").append(tenantId);
            sb.append("_p").append(projectId == null ? 0 : projectId);
            sb.append("_r").append(runId == null ? 0 : runId);
            sb.append("_b").append(batchId == null ? 0 : batchId);
            return Paths.get(System.getProperty("java.io.tmpdir"), "aipal-workspaces", sb.toString())
                    .toAbsolutePath().normalize();
        }
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
