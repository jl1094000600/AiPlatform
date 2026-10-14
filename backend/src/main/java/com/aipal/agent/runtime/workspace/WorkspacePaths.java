package com.aipal.agent.runtime.workspace;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Path containment helpers. Rejects absolute paths, parent traversal and symlink-style escapes.
 */
public final class WorkspacePaths {

    private WorkspacePaths() {
    }

    /**
     * Resolve {@code relativePath} strictly inside {@code root}.
     *
     * @throws IllegalArgumentException when the path is blank, absolute, or escapes the root
     */
    public static Path resolveInside(Path root, String relativePath) {
        if (root == null) {
            throw new IllegalArgumentException("workspace root is required");
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        String normalized = relativePath.trim().replace('\\', '/');
        if (normalized.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("path contains illegal characters");
        }
        if (isAbsolute(normalized)) {
            throw new IllegalArgumentException("absolute path is not allowed: " + relativePath);
        }
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment) || ".".equals(segment)) {
                throw new IllegalArgumentException("path escapes workspace: " + relativePath);
            }
        }
        Path rootNormalized = root.toAbsolutePath().normalize();
        Path resolved = rootNormalized.resolve(normalized).normalize();
        if (!resolved.startsWith(rootNormalized)) {
            throw new IllegalArgumentException("path escapes workspace: " + relativePath);
        }
        return resolved;
    }

    private static boolean isAbsolute(String normalized) {
        if (normalized.startsWith("/")) {
            return true;
        }
        if (normalized.startsWith("//")) {
            return true;
        }
        // Windows drive letters such as C:/...
        return normalized.length() >= 2
                && Character.isLetter(normalized.charAt(0))
                && normalized.charAt(1) == ':';
    }

    /** Convert a command list or single string into a display token. */
    public static String displayCommand(java.util.List<String> command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        return String.join(" ", command);
    }

    public static Path toPath(String value) {
        return Paths.get(value);
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
