package com.aipal.agent.runtime.tool.builtin;

import com.aipal.agent.runtime.tool.AgentTool;
import com.aipal.agent.runtime.tool.ToolInvocationContext;
import com.aipal.agent.runtime.tool.ToolResult;
import com.aipal.agent.runtime.workspace.CommandResult;
import com.aipal.agent.runtime.workspace.ShellCommandGuard;
import com.aipal.agent.runtime.workspace.Workspace;
import com.aipal.agent.runtime.workspace.WorkspaceManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Execute a bounded shell command inside the run workspace. Risk: EXEC (policy required).
 * Dangerous commands (rm -rf, curl|sh, sudo, mkfs, dd, ...) are rejected before execution.
 */
@Component
@RequiredArgsConstructor
public class ShellExecTool implements AgentTool {

    public static final String TOOL_NAME = "shell.exec";
    public static final String RISK_LEVEL = "DESTRUCTIVE";
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int MAX_TIMEOUT_SECONDS = 600;
    private static final int MAX_OUTPUT_SUMMARY = 2000;

    private final WorkspaceManager workspaceManager;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public ToolResult execute(ToolInvocationContext context, Object input) {
        Map<?, ?> payload = asMap(input);
        List<String> command = parseCommand(payload);
        String display = String.join(" ", command);
        ShellCommandGuard.requireSafeCommand(display);

        int timeoutSeconds = boundedInt(payload.get("timeoutSeconds"), DEFAULT_TIMEOUT_SECONDS, 1, MAX_TIMEOUT_SECONDS);
        Workspace workspace = workspaceManager.acquire(context);
        CommandResult result = workspace.exec(command, Duration.ofSeconds(timeoutSeconds));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("command", display);
        output.put("exitCode", result.exitCode());
        output.put("timedOut", result.timedOut());
        output.put("stdout", truncate(result.stdout(), MAX_OUTPUT_SUMMARY));
        output.put("stderr", truncate(result.stderr(), MAX_OUTPUT_SUMMARY));
        output.put("summary", result.summary());
        String summary = "shell.exec " + (result.succeeded() ? "succeeded" : "failed")
                + " exit=" + result.exitCode() + (result.timedOut() ? " (timed out)" : "");
        return new ToolResult(output, summary, estimateTokens(display),
                estimateTokens(result.stdout()) + estimateTokens(result.stderr()));
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> asMap(Object input) {
        if (input instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("shell.exec input must be an object");
    }

    private List<String> parseCommand(Map<?, ?> payload) {
        Object command = payload.get("command");
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (command instanceof List<?> list) {
            List<String> tokens = new ArrayList<>();
            for (Object item : list) {
                if (item == null || String.valueOf(item).isBlank()) {
                    continue;
                }
                tokens.add(String.valueOf(item));
            }
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException("command is required");
            }
            return tokens;
        }
        String value = String.valueOf(command).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("command is required");
        }
        // Single string is passed through the OS shell; guard already inspected the raw text.
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return windows
                ? List.of("cmd.exe", "/c", value)
                : List.of("sh", "-c", value);
    }

    private int boundedInt(Object value, int defaultValue, int min, int max) {
        if (value == null) {
            return defaultValue;
        }
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private int estimateTokens(String value) {
        return value == null ? 0 : Math.max(1, value.length() / 4);
    }
}
