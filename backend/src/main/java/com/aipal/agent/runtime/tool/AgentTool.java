package com.aipal.agent.runtime.tool;

public interface AgentTool {
    String name();

    ToolResult execute(ToolInvocationContext context, Object input) throws Exception;
}
