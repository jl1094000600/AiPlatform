package com.aipal.agent.runtime.workspace;

import com.aipal.agent.runtime.tool.ToolInvocationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Run-scoped workspace access for coding tools. One workspace per (tenant, project/run/batch)
 * so tools and build/test share an isolated sandbox. F01: identity is mandatory; F04: release
 * on terminal/cancel.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceManager {

    private final WorkspaceFactory workspaceFactory;
    private final Map<String, Workspace> active = new ConcurrentHashMap<>();

    public Workspace acquire(ToolInvocationContext context) {
        if (context == null || context.tenantId() == null || context.tenantId() <= 0
                || context.runId() == null || context.runId() <= 0) {
            throw new IllegalArgumentException("tenantId and runId are required for tool workspace isolation");
        }
        return acquire(new WorkspaceFactory.WorkspaceIdentity(
                context.tenantId(), null, context.runId(), null, null));
    }

    public Workspace acquire(WorkspaceFactory.WorkspaceIdentity identity) {
        String k = key(identity);
        return active.computeIfAbsent(k, ignored -> workspaceFactory.openToolWorkspace(identity));
    }

    /** Register a pre-built workspace (tests, pipeline stages). */
    public Workspace bind(WorkspaceFactory.WorkspaceIdentity identity, Workspace workspace) {
        return active.put(key(identity), workspace);
    }

    public void release(WorkspaceFactory.WorkspaceIdentity identity) {
        if (identity == null) {
            return;
        }
        release(key(identity));
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
        if (tenantId == null || tenantId <= 0 || runId == null || runId <= 0) {
            return;
        }
        release(key(new WorkspaceFactory.WorkspaceIdentity(tenantId, null, runId, null, null)));
    }

    public String key(WorkspaceFactory.WorkspaceIdentity identity) {
        return identity.tenantId() + ":" + identity.projectId() + ":" + identity.runId() + ":" + identity.batchId();
    }

    public String key(Long tenantId, Long runId) {
        return key(new WorkspaceFactory.WorkspaceIdentity(tenantId, null, runId, null, null));
    }

    /** Non-creating lookup for queries. Returns null when no workspace is bound. */
    public Workspace find(WorkspaceFactory.WorkspaceIdentity identity) {
        return identity == null ? null : active.get(key(identity));
    }

    public Workspace find(Long tenantId, Long runId) {
        return find(new WorkspaceFactory.WorkspaceIdentity(tenantId, null, runId, null, null));
    }

    public boolean isActive(WorkspaceFactory.WorkspaceIdentity identity) {
        return identity != null && active.containsKey(key(identity));
    }
}
