package com.aipal.exception;

/**
 * 心跳异常
 */
public class HeartbeatException extends RuntimeException {
    private final String agentCode;
    private final String instanceId;

    public HeartbeatException(String agentCode, String instanceId, String message) {
        super(message);
        this.agentCode = agentCode;
        this.instanceId = instanceId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
