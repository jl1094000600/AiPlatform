package com.aipal.agent.runtime.workspace;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rejects destructive or network-piping shell commands before they reach a workspace.
 * A deny-list is intentionally strict: coding tools only need build/test-style commands.
 */
public final class ShellCommandGuard {

    private static final List<Pattern> DANGEROUS = List.of(
            // pipe downloaders into a shell
            Pattern.compile("(curl|wget)\\b[^|\\n]*\\|\\s*(sh|bash|zsh|ksh|python[0-9.]*)\\b", Pattern.CASE_INSENSITIVE),
            // privilege escalation / disk / system
            Pattern.compile("(^|[\\s;&|])sudo\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[\\s;&|])mkfs(\\.[a-z0-9]+)?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[\\s;&|])dd\\s+if=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[\\s;&|])(shutdown|reboot|halt|poweroff)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[\\s;&|])(fdisk|parted|wipefs|mkswap)\\b", Pattern.CASE_INSENSITIVE),
            // raw device writes
            Pattern.compile(">\\s*/dev/(sd|hd|nvme|vd|xvd|dm-)", Pattern.CASE_INSENSITIVE),
            // fork bomb
            Pattern.compile(":\\s*\\(\\s*\\)\\s*\\{"),
            // Windows destructive
            Pattern.compile("(^|[\\s;&|])(format|diskpart)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[\\s;&|])(del|rd|rmdir)\\s+/[sfq]", Pattern.CASE_INSENSITIVE)
    );

    private static final Pattern RM_COMMAND = Pattern.compile("(^|[\\s;&|])rm\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RM_RECURSIVE_FLAG = Pattern.compile("(^|\\s)-[a-zA-Z]*r", Pattern.CASE_INSENSITIVE);
    private static final Pattern RM_FORCE_FLAG = Pattern.compile("(^|\\s)-[a-zA-Z]*f", Pattern.CASE_INSENSITIVE);

    private ShellCommandGuard() {
    }

    /**
     * @throws IllegalArgumentException when the command matches a dangerous pattern
     */
    public static void requireSafeCommand(String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        String normalized = command.trim().toLowerCase(Locale.ROOT);
        for (Pattern pattern : DANGEROUS) {
            if (pattern.matcher(normalized).find()) {
                throw new IllegalArgumentException("dangerous command rejected: " + summarize(command));
            }
        }
        if (RM_COMMAND.matcher(normalized).find()
                && RM_RECURSIVE_FLAG.matcher(normalized).find()
                && RM_FORCE_FLAG.matcher(normalized).find()) {
            throw new IllegalArgumentException("dangerous command rejected: " + summarize(command));
        }
    }

    private static String summarize(String command) {
        String trimmed = command.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120) + "...";
    }
}
