package com.aipal.agent.runtime.tool.builtin;

import com.aipal.agent.runtime.tool.AgentTool;
import com.aipal.agent.runtime.tool.ToolInvocationContext;
import com.aipal.agent.runtime.tool.ToolResult;
import com.aipal.agent.runtime.workspace.Workspace;
import com.aipal.agent.runtime.workspace.WorkspaceManager;
import com.aipal.agent.runtime.workspace.WorkspacePaths;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Write a file into the run workspace. Risk: WRITE (policy required).
 * Rejects absolute paths and parent-directory escapes.
 */
@Component
@RequiredArgsConstructor
public class FsWriteTool implements AgentTool {

    public static final String TOOL_NAME = "fs.write";
    public static final String RISK_LEVEL = "MUTATING";

    private final WorkspaceManager workspaceManager;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public ToolResult execute(ToolInvocationContext context, Object input) {
        Map<?, ?> payload = asMap(input);
        String path = requiredString(payload, "path");
        String content = payload.get("content") == null ? "" : String.valueOf(payload.get("content"));
        Workspace workspace = workspaceManager.acquire(context);
        WorkspacePaths.resolveInside(workspace.workDir(), path);
        try {
            workspace.putFile(path, content);
        } catch (Exception e) {
            throw new IllegalArgumentException("fs.write failed for path " + path + ": " + e.getMessage(), e);
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("path", path);
        output.put("size", content.length());
        output.put("written", Boolean.TRUE);
        return new ToolResult(output, "fs.write " + path + " (" + content.length() + " chars)",
                estimateTokens(content), 20);
    }

    private Map<?, ?> asMap(Object input) {
        if (input instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("fs.write input must be an object");
    }

    private String requiredString(Map<?, ?> payload, String field) {
        Object value = payload.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return String.valueOf(value).trim();
    }

    private int estimateTokens(String value) {
        return value == null ? 0 : Math.max(1, value.length() / 4);
    }
}
