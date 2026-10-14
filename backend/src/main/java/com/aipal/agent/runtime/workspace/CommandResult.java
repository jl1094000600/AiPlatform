package com.aipal.agent.runtime.workspace;

import java.time.Duration;

/**
 * Result of a sandboxed command execution.
 */
public record CommandResult(int exitCode, String stdout, String stderr, boolean timedOut, Duration duration,
                            String command) {

    public static CommandResult success(String command, String stdout, Duration duration) {
        return new CommandResult(0, stdout == null ? "" : stdout, "", false, duration, command);
    }

    public static CommandResult failure(String command, int exitCode, String stdout, String stderr, Duration duration) {
        return new CommandResult(exitCode, stdout == null ? "" : stdout, stderr == null ? "" : stderr,
                false, duration, command);
    }

    public static CommandResult timedOut(String command, String stdout, String stderr, Duration duration) {
        return new CommandResult(-1, stdout == null ? "" : stdout, stderr == null ? "" : stderr,
                true, duration, command);
    }

    public boolean succeeded() {
        return !timedOut && exitCode == 0;
    }

    /** Short stdout/stderr digest suitable for audit output. */
    public String summary() {
        String out = truncate(stdout, 400);
        String err = truncate(stderr, 400);
        StringBuilder sb = new StringBuilder();
        sb.append("exit=").append(exitCode).append(timedOut ? " (timed out)" : "");
        if (!out.isEmpty()) {
            sb.append(" stdout: ").append(out);
        }
        if (!err.isEmpty()) {
            sb.append(" stderr: ").append(err);
        }
        return sb.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "...";
    }
}
