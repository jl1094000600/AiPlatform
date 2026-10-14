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
 * Read a file from the run workspace. Risk: READ (default-allowed).
 * Rejects absolute paths and parent-directory escapes.
 */
@Component
@RequiredArgsConstructor
public class FsReadTool implements AgentTool {

    public static final String TOOL_NAME = "fs.read";
    public static final String RISK_LEVEL = "READ_ONLY";

    private final WorkspaceManager workspaceManager;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public ToolResult execute(ToolInvocationContext context, Object input) {
        Map<?, ?> payload = asMap(input);
        String path = requiredString(payload, "path");
        Workspace workspace = workspaceManager.acquire(context);
        // Validate containment even before delegating to the workspace implementation.
        WorkspacePaths.resolveInside(workspace.workDir(), path);
        String content;
        try {
            content = workspace.getFile(path);
        } catch (Exception e) {
            throw new IllegalArgumentException("fs.read failed for path " + path + ": " + e.getMessage(), e);
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("path", path);
        output.put("content", content);
        output.put("size", content == null ? 0 : content.length());
        return new ToolResult(output, "fs.read " + path + " (" + (content == null ? 0 : content.length()) + " chars)",
                estimateTokens(path), estimateTokens(content));
    }

    private Map<?, ?> asMap(Object input) {
        if (input instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("fs.read input must be an object");
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
