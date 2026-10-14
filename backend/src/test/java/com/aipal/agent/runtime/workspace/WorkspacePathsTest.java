package com.aipal.agent.runtime.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspacePathsTest {

    @TempDir
    Path root;

    @Test
    void resolvesRelativePathInsideRoot() {
        Path resolved = WorkspacePaths.resolveInside(root, "src/main/App.java");
        assertEquals(root.toAbsolutePath().normalize().resolve("src/main/App.java"), resolved);
    }

    @Test
    void rejectsParentTraversal() {
        assertThrows(IllegalArgumentException.class, () -> WorkspacePaths.resolveInside(root, "../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> WorkspacePaths.resolveInside(root, "a/../../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> WorkspacePaths.resolveInside(root, "..\\secret.txt"));
    }

    @Test
    void rejectsAbsolutePaths() {
        assertThrows(IllegalArgumentException.class, () -> WorkspacePaths.resolveInside(root, "/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> WorkspacePaths.resolveInside(root, "C:/Windows/System32"));
        assertThrows(IllegalArgumentException.class, () -> WorkspacePaths.resolveInside(root, "\\\\server\\share"));
    }

    @Test
    void rejectsBlankPath() {
        assertThrows(IllegalArgumentException.class, () -> WorkspacePaths.resolveInside(root, ""));
        assertThrows(IllegalArgumentException.class, () -> WorkspacePaths.resolveInside(root, null));
    }

    @Test
    void rejectsDotSegment() {
        assertThrows(IllegalArgumentException.class, () -> WorkspacePaths.resolveInside(root, "./../x"));
    }
}
