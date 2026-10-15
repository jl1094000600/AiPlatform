package com.aipal.service;

import java.util.Locale;
import java.util.Set;

final class AutomationCommandGuard {
    private static final Set<String> ALLOWED_EXECUTABLES = Set.of(
            "mvn", "mvn.cmd", "mvnw", "mvnw.cmd", "./mvnw",
            "npm", "npm.cmd", "pnpm", "pnpm.cmd", "yarn", "yarn.cmd", "npx", "npx.cmd",
            "gradle", "gradle.bat", "gradlew", "gradlew.bat", "./gradlew",
            "docker"
    );
    private static final Set<String> BLOCKED_EXECUTABLES = Set.of(
            "cmd", "cmd.exe", "powershell", "powershell.exe", "pwsh", "pwsh.exe",
            "bash", "sh", "wsl", "python", "python.exe", "python3", "node", "node.exe"
    );

    private AutomationCommandGuard() {
    }

    static void requireSafeCommand(String command, String fieldName) {
        if (command == null || command.isBlank()) {
            // fail-closed: a missing command must be rejected, never treated as allowed
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        String trimmed = command.trim();
        if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(fieldName + " must be a single command");
        }
        if (trimmed.contains("&") || trimmed.contains("||") || trimmed.contains(";")
                || trimmed.contains("|") || trimmed.contains(">") || trimmed.contains("<")
                || trimmed.contains("`") || trimmed.contains("$(") || trimmed.contains("^")) {
            throw new IllegalArgumentException(fieldName + " contains unsafe shell operators");
        }
        String executable = firstToken(trimmed).toLowerCase(Locale.ROOT);
        if (BLOCKED_EXECUTABLES.contains(executable) || !ALLOWED_EXECUTABLES.contains(executable)) {
            throw new IllegalArgumentException(fieldName + " executable is not allowed: " + executable);
        }
    }

    private static String firstToken(String command) {
        int index = command.indexOf(' ');
        return index < 0 ? command : command.substring(0, index);
    }
}
