package com.aipal.agent.runtime.tool;

public record ToolResult(Object output, String outputSummary, int inputTokens, int outputTokens) {
    public static ToolResult of(Object output) {
        return new ToolResult(output, null, 0, 0);
    }
}
