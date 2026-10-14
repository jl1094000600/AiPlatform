package com.aipal.service;

/**
 * Unified result of a chat-completion model call, including token usage.
 */
public record ModelCallResult(String content, int inputTokens, int outputTokens, int totalTokens) {

    public static ModelCallResult empty() {
        return new ModelCallResult("", 0, 0, 0);
    }

    public String contentOrEmpty() {
        return content == null ? "" : content;
    }
}
