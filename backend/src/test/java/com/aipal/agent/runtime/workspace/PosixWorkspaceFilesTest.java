package com.aipal.agent.runtime.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class PosixWorkspaceFilesTest {
    @TempDir
    Path temporary;

    @Test
    void firstWriteCreatesMissingRootAndNestedDirectories() throws Exception {
        Path root = temporary.resolve("new/run/workspace");
        try (LocalWorkspace workspace = LocalWorkspace.wrap(root)) {
            workspace.putFile("src/main/你好.txt", "你好，世界🌏".repeat(5000));
            assertEquals("你好，世界🌏".repeat(5000), workspace.getFile("src/main/你好.txt"));
            workspace.putFile("src/main/你好.txt", "");
            assertEquals("", workspace.getFile("src/main/你好.txt"));
        }
        assertEquals(PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(root.resolve("src/main/你好.txt")));
        assertEquals(PosixFilePermissions.fromString("rwx------"),
                Files.getPosixFilePermissions(root.resolve("src/main")));
    }

    @Test
    void rejectsExistingLeafAndDirectorySymlinksWithoutChangingOutsideFiles() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path sentinel = Files.writeString(outside.resolve("sentinel"), "outside");
        Path root = Files.createDirectory(temporary.resolve("workspace"));
        try (LocalWorkspace workspace = LocalWorkspace.wrap(root)) {
            Files.createSymbolicLink(root.resolve("leaf"), sentinel);
            Files.createSymbolicLink(root.resolve("directory"), outside);
            Files.createSymbolicLink(root.resolve("dangling"), outside.resolve("missing"));
            for (String name : new String[]{"leaf", "directory/sentinel", "dangling"}) {
                assertThrows(IllegalArgumentException.class, () -> workspace.getFile(name));
                assertThrows(IllegalArgumentException.class, () -> workspace.putFile(name, "attack"));
            }
        }
        assertEquals("outside", Files.readString(sentinel));
        assertFalse(Files.exists(outside.resolve("missing")));
    }

    @Test
    void rejectsSymlinkAtWorkspaceRoot() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path root = Files.createSymbolicLink(temporary.resolve("workspace"), outside);
        assertThrows(UncheckedIOException.class, () -> LocalWorkspace.wrap(root));
    }

    @Test
    void heldParentDoesNotFollowDirectorySwappedAfterValidation() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Files.writeString(outside.resolve("sentinel"), "outside");
        Path root = Files.createDirectory(temporary.resolve("workspace"));
        Path source = Files.createDirectory(root.resolve("src"));
        Files.writeString(source.resolve("sentinel"), "inside");
        PosixWorkspaceFiles files = new PosixWorkspaceFiles(root);
        try (var parent = files.openParent("src/sentinel", true)) {
            Files.move(source, root.resolve("original"));
            Files.createSymbolicLink(source, outside);
            assertEquals("inside", parent.read("sentinel"));
            parent.write("sentinel", "updated inside");
            parent.write("new-file", "inside");
        }
        assertEquals("outside", Files.readString(outside.resolve("sentinel")));
        assertFalse(Files.exists(outside.resolve("new-file")));
        assertEquals("updated inside", Files.readString(root.resolve("original/sentinel")));
    }

    @Test
    void heldRootDoesNotFollowRootSwappedAfterOpen() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Files.writeString(outside.resolve("sentinel"), "outside");
        Path root = Files.createDirectory(temporary.resolve("workspace"));
        PosixWorkspaceFiles files = new PosixWorkspaceFiles(root);
        try (var parent = files.openParent("sentinel", true)) {
            Files.move(root, temporary.resolve("original"));
            Files.createSymbolicLink(root, outside);
            parent.write("sentinel", "inside");
            assertEquals("inside", parent.read("sentinel"));
        }
        assertEquals("outside", Files.readString(outside.resolve("sentinel")));
        assertThrows(IOException.class, () -> files.openParent("sentinel", false));
    }

    @Test
    void lateLeafSymlinkCannotReadOrTruncateItsTarget() throws Exception {
        Path outside = Files.writeString(temporary.resolve("outside"), "outside");
        Path root = Files.createDirectory(temporary.resolve("workspace"));
        PosixWorkspaceFiles files = new PosixWorkspaceFiles(root);
        try (var parent = files.openParent("leaf", true)) {
            Files.createSymbolicLink(root.resolve("leaf"), outside);
            assertThrows(IOException.class, () -> parent.read("leaf"));
            // Atomic replacement only removes the link entry, never opens its target.
            parent.write("leaf", "inside");
            assertEquals("inside", parent.read("leaf"));
        }
        assertEquals("outside", Files.readString(outside));
        assertFalse(Files.isSymbolicLink(root.resolve("leaf")));
    }

    @Test
    void replacingHardlinkedOutputDoesNotTruncateExternalFile() throws Exception {
        Path outside = Files.writeString(temporary.resolve("outside"), "outside");
        Path root = Files.createDirectory(temporary.resolve("workspace"));
        Files.createLink(root.resolve("output"), outside);
        try (LocalWorkspace workspace = LocalWorkspace.wrap(root)) {
            workspace.putFile("output", "inside");
            assertEquals("inside", workspace.getFile("output"));
        }
        assertEquals("outside", Files.readString(outside));
    }

    @Test
    void readsRejectDirectoriesAndFifosWithoutBlocking() throws Exception {
        Path root = temporary.resolve("workspace");
        try (LocalWorkspace workspace = LocalWorkspace.wrap(root)) {
            Files.createDirectory(root.resolve("directory"));
            assertThrows(IOException.class, () -> workspace.getFile("directory"));
            Process fifo = new ProcessBuilder("mkfifo", root.resolve("pipe").toString()).start();
            assertEquals(0, fifo.waitFor());
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
                    () -> assertThrows(IOException.class, () -> workspace.getFile("pipe")));
        }
    }

    @Test
    void concurrentWritersCanCreateSameParentWithoutUnsafeFallback() throws Exception {
        Path root = temporary.resolve("workspace");
        try (LocalWorkspace workspace = LocalWorkspace.wrap(root);
             var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 20; i++) {
                int index = i;
                tasks.add(executor.submit(() -> {
                    workspace.putFile("shared/nested/" + index, "value-" + index);
                    return null;
                }));
            }
            for (var task : tasks) task.get();
            for (int i = 0; i < 20; i++) assertEquals("value-" + i, workspace.getFile("shared/nested/" + i));
        }
        try (var files = Files.list(root.resolve("shared/nested"))) {
            assertEquals(20, files.count());
        }
    }

    @Test
    void dockerFileOperationsUseTheSameNoFollowBoundary() throws Exception {
        Path root = temporary.resolve("workspace");
        Path outside = Files.writeString(temporary.resolve("outside"), "outside");
        DockerWorkspace workspace = new DockerWorkspace(root, "unused", "unused");
        workspace.putFile("src/file", "inside");
        assertEquals("inside", workspace.getFile("src/file"));
        Files.createSymbolicLink(root.resolve("escape"), outside);
        assertThrows(IllegalArgumentException.class, () -> workspace.getFile("escape"));
        assertThrows(IllegalArgumentException.class, () -> workspace.putFile("escape", "attack"));
        assertEquals("outside", Files.readString(outside));
    }
}
