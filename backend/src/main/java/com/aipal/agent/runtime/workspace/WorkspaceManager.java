package com.aipal.agent.runtime.workspace;

import com.aipal.agent.runtime.tool.ToolInvocationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Run-scoped workspace access for coding tools. One workspace per (tenant, run) so
 * fs.read / fs.write / shell.exec share the same sandbox during a run.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceManager {

    private final WorkspaceFactory workspaceFactory;
    private final Map<String, Workspace> active = new ConcurrentHashMap<>();

    public Workspace acquire(ToolInvocationContext context) {
        return acquire(key(context.tenantId(), context.runId()));
    }

    public Workspace acquire(String key) {
        return active.computeIfAbsent(key, k -> workspaceFactory.openLocalTemp());
    }

    /** Register a pre-built workspace (tests, pipeline stages). */
    public Workspace bind(String key, Workspace workspace) {
        return active.put(key, workspace);
    }

    public void release(String key) {
        Workspace workspace = active.remove(key);
        if (workspace != null) {
            try {
                workspace.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    public void release(Long tenantId, Long runId) {
        release(key(tenantId, runId));
    }

    public String key(Long tenantId, Long runId) {
        return (tenantId == null ? "0" : tenantId) + ":" + (runId == null ? "0" : runId);
    }

    /** Non-creating lookup for queries. Returns null when no workspace is bound. */
    public Workspace find(String key) {
        return active.get(key);
    }

    public Workspace find(Long tenantId, Long runId) {
        return find(key(tenantId, runId));
    }

    /** True when a live workspace is currently bound to this key. */
    public boolean isActive(String key) {
        return active.containsKey(key);
    }
}

