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

    @Test
    void rejectsSymlinkPointingOutsideRoot(@TempDir Path outside) throws Exception {
        Path secret = outside.resolve("secret.txt");
        java.nio.file.Files.writeString(secret, "classified");
        Path link = root.resolve("leak.txt");
        java.nio.file.Files.createSymbolicLink(link, secret);

        assertThrows(IllegalArgumentException.class, () -> WorkspacePaths.resolveInside(root, "leak.txt"));
    }

    @Test
    void rejectsNestedSymlinkPointingOutsideRoot(@TempDir Path outside) throws Exception {
        Path secretDir = outside.resolve("secrets");
        java.nio.file.Files.createDirectories(secretDir);
        Path linkDir = root.resolve("vendor");
        java.nio.file.Files.createSymbolicLink(linkDir, secretDir);

        assertThrows(IllegalArgumentException.class,
                () -> WorkspacePaths.resolveInside(root, "vendor/keys.pem"));
    }

    @Test
    void allowsRealFileInsideRoot() throws Exception {
        Path file = root.resolve("src/App.java");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, "class App {}");
        assertEquals(file.toAbsolutePath().normalize(),
                WorkspacePaths.resolveInside(root, "src/App.java"));
    }
}
