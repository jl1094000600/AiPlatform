package com.aipal.agent.runtime.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
     * Checks both lexical containment and real path containment so a symlink
     * created inside the workspace cannot point at host files outside it.
     * This is a snapshot check, not an I/O capability: callers must use
     * directory-relative NOFOLLOW operations for the actual read/write.
     *
     * @throws IllegalArgumentException when the path is blank, absolute, or escapes the root
     */
    public static Path resolveInside(Path root, String relativePath) {
        Path resolved = resolveLexicallyInside(root, relativePath);
        requireRealPathInside(root.toAbsolutePath().normalize(), resolved, relativePath);
        return resolved;
    }

    /** Lexical validation only; actual I/O must additionally use directory-relative NOFOLLOW operations. */
    static Path resolveLexicallyInside(Path root, String relativePath) {
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

    /**
     * Walk existing ancestors and verify no symlink carries the path outside {@code root}.
     * Non-existing tail segments are fine: they will be created as real files/dirs.
     */
    private static void requireRealPathInside(Path rootNormalized, Path resolved, String relativePath) {
        try {
            Path realRoot = rootNormalized.toRealPath();
            Path cursor = resolved;
            while (cursor != null && cursor.startsWith(rootNormalized)) {
                if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
                    Path realCursor = cursor.toRealPath();
                    if (!realCursor.startsWith(realRoot)) {
                        throw new IllegalArgumentException("path escapes workspace via symlink: " + relativePath);
                    }
                    return;
                }
                cursor = cursor.getParent();
            }
            if (cursor == null || !cursor.startsWith(rootNormalized)) {
                throw new IllegalArgumentException("path escapes workspace: " + relativePath);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("path cannot be resolved safely: " + relativePath, e);
        }
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
