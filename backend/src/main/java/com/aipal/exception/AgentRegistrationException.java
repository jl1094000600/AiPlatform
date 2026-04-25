package com.aipal.exception;

/**
 * Agent注册异常
 */
public class AgentRegistrationException extends RuntimeException {
    private final String agentCode;
    private final String errorCode;

    public AgentRegistrationException(String agentCode, String errorCode, String message) {
        super(message);
        this.agentCode = agentCode;
        this.errorCode = errorCode;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
