package com.aipal.controller;

import com.aipal.agent.runtime.workspace.Workspace;
import com.aipal.agent.runtime.workspace.WorkspaceManager;
import com.aipal.common.Result;
import com.aipal.entity.AgentRun;
import com.aipal.security.RequirePermission;
import com.aipal.service.AgentRunService;
import com.aipal.service.AgentRunStreamEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Workspace query API for the agent-run console. Field names are the frontend contract:
 * {@code status}, {@code workDir}, {@code files:[{path,size}]}, {@code recentLogs}.
 */
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private static final int MAX_FILES = 500;
    private static final int MAX_LOG_LINES = 700;

    private final AgentRunService agentRunService;
    private final WorkspaceManager workspaceManager;
    private final AgentRunStreamEventService streamEventService;

    @GetMapping("/{runId}")
    @RequirePermission("agent:invoke")
    public Result<WorkspaceView> getWorkspace(@PathVariable Long runId) {
        AgentRun run = agentRunService.get(runId);
        Workspace workspace = workspaceManager.find(run.getTenantId(), run.getId());
        if (workspace != null) {
            return Result.success(fromWorkspace(run, workspace));
        }
        return Result.success(placeholder(run));
    }

    private WorkspaceView fromWorkspace(AgentRun run, Workspace workspace) {
        Path workDir = workspace.workDir();
        String health = workspace.health();
        String status = mapHealth(health, run.getStatus());
        String workDirPath = workDir == null ? null : workDir.toAbsolutePath().normalize().toString();
        return new WorkspaceView(status, workDirPath, listFiles(workDir), recentLogs(run));
    }

    private WorkspaceView placeholder(AgentRun run) {
        return new WorkspaceView(mapHealth(null, run.getStatus()), null, List.of(), recentLogs(run));
    }

    private List<FileView> listFiles(Path workDir) {
        if (workDir == null || !Files.isDirectory(workDir)) return List.of();
        List<FileView> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(workDir)) {
            walk.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(MAX_FILES)
                    .forEach(path -> {
                        try {
                            String rel = workDir.relativize(path).toString().replace('\\', '/');
                            files.add(new FileView(rel, Files.size(path)));
                        } catch (IOException ignored) {
                            // skip files that disappear mid-walk
                        }
                    });
        } catch (IOException ignored) {
            return List.of();
        }
        return files;
    }

    /** Recent run stream events rendered as plain log lines (content-light payloads only). */
    private String recentLogs(AgentRun run) {
        try {
            List<AgentRunStreamEventService.RunStreamEventView> events =
                    streamEventService.listForRun(run.getTenantId(), run.getId(), null, MAX_LOG_LINES, false);
            if (events.isEmpty()) return "";
            StringBuilder logs = new StringBuilder();
            for (AgentRunStreamEventService.RunStreamEventView event : events) {
                if (logs.length() > 0) logs.append('\n');
                logs.append('[').append(event.eventType()).append("] #").append(event.sequenceNo());
                if (event.payload() != null && !event.payload().isEmpty()) {
                    logs.append(' ').append(event.payload());
                }
            }
            return logs.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private String mapHealth(String health, String runStatus) {
        if ("READY".equals(health)) return "READY";
        if ("CLOSED".equals(health)) return "DESTROYED";
        if ("MISSING_ROOT".equals(health)) return "FAILED";
        if (runStatus == null) return "CREATED";
        return switch (runStatus) {
            case "QUEUED" -> "CREATED";
            case "RUNNING", "WAITING_APPROVAL", "WAITING_USER_INPUT" -> "RUNNING";
            case "SUCCEEDED" -> "STOPPED";
            case "FAILED" -> "FAILED";
            case "TIMEOUT" -> "TIMEOUT";
            case "CANCELLED" -> "STOPPED";
            default -> "CREATED";
        };
    }

    /** Frontend workspace contract. */
    public record WorkspaceView(String status, String workDir, List<FileView> files, String recentLogs) {}

    public record FileView(String path, Long size) {}
}
