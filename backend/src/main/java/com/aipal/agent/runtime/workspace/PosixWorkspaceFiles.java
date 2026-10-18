package com.aipal.agent.runtime.workspace;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * File access anchored to directory descriptors, including mkdir. No path is reopened
 * after validation. The configured root's existing ancestors are application-owned;
 * the root itself and all paths beneath it are untrusted and never follow links.
 * Java's SecureDirectoryStream is unavailable on macOS and has no mkdirat operation.
 */
final class PosixWorkspaceFiles {
    private final Path root;
    private final Path anchor;
    private final Path rootFromAnchor;

    PosixWorkspaceFiles(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Path existingParent = this.root.getParent();
        if (existingParent == null) {
            throw new IOException("filesystem root cannot be used as a workspace");
        }
        while (!Files.exists(existingParent, LinkOption.NOFOLLOW_LINKS)) {
            existingParent = existingParent.getParent();
        }
        // Resolve trusted system aliases (e.g. macOS /var) only once, above the root.
        // Subsequent operations walk even this canonical path with NOFOLLOW.
        anchor = existingParent.toRealPath();
        rootFromAnchor = existingParent.relativize(this.root);
        try (Directory ignored = openRoot(true)) {
            // Initialize a fresh run before tools perform their snapshot path checks.
        }
    }

    Directory openParent(String relativePath, boolean create) throws IOException {
        Path target = WorkspacePaths.resolveInside(root, relativePath);
        Path relative = root.relativize(target);
        Directory directory = openRoot(create);
        try {
            for (int i = 0; i < relative.getNameCount() - 1; i++) {
                Directory child = directory.child(relative.getName(i).toString(), create);
                directory.close();
                directory = child;
            }
            return directory;
        } catch (IOException | RuntimeException e) {
            directory.close();
            throw e;
        }
    }

    String fileName(String relativePath) {
        return root.relativize(WorkspacePaths.resolveLexicallyInside(root, relativePath))
                .getFileName().toString();
    }

    private Directory openRoot(boolean create) throws IOException {
        NativeApi api = NativeApi.instance();
        Directory directory = new Directory(api, api.openDirectory("/"));
        try {
            for (Path segment : anchor) {
                Directory child = directory.child(segment.toString(), false);
                directory.close();
                directory = child;
            }
            for (Path segment : rootFromAnchor) {
                Directory child = directory.child(segment.toString(), create);
                directory.close();
                directory = child;
            }
            return directory;
        } catch (IOException | RuntimeException e) {
            directory.close();
            throw e;
        }
    }

    /** A held parent remains pinned even when its pathname is replaced mid-operation. */
    static final class Directory implements AutoCloseable {
        private final NativeApi api;
        private int fd;

        private Directory(NativeApi api, int fd) {
            this.api = api;
            this.fd = fd;
        }

        private Directory child(String name, boolean create) throws IOException {
            int child = api.lib.openat(fd, name, api.directoryFlags(), 0);
            if (child < 0) {
                int error = Native.getLastError();
                if (!create || error != 2) throw api.error("open directory", error);
                int made = api.lib.mkdirat(fd, name, 0700);
                int mkdirError = made < 0 ? Native.getLastError() : 0;
                if (made < 0 && mkdirError != 17) throw api.error("create directory", mkdirError);
                // A concurrent creator may have installed a link: always reopen NOFOLLOW.
                child = api.lib.openat(fd, name, api.directoryFlags(), 0);
                if (child < 0) throw api.error("open created directory", Native.getLastError());
            }
            return new Directory(api, child);
        }

        String read(String name) throws IOException {
            requireName(name);
            int file = api.lib.openat(fd, name, api.noFollow | api.closeOnExec | api.nonBlock, 0);
            if (file < 0) throw api.error("open file for reading", Native.getLastError());
            try {
                api.requireRegularFile(file);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                while (true) {
                    long count = api.lib.read(file, buffer, new NativeLong(buffer.length)).longValue();
                    if (count < 0) {
                        int error = Native.getLastError();
                        if (error == 4) continue;
                        throw api.error("read file", error);
                    }
                    if (count == 0) return output.toString(StandardCharsets.UTF_8);
                    output.write(buffer, 0, (int) count);
                }
            } finally {
                api.lib.close(file);
            }
        }

        void write(String name, String content) throws IOException {
            requireName(name);
            String temporary = ".aipal-write-" + UUID.randomUUID();
            int file = api.lib.openat(fd, temporary,
                    1 | api.create | api.exclusive | api.noFollow | api.closeOnExec, 0600);
            if (file < 0) throw api.error("create file", Native.getLastError());
            try {
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                try (Memory buffer = new Memory(Math.max(1, bytes.length))) {
                    if (bytes.length > 0) buffer.write(0, bytes, 0, bytes.length);
                    long offset = 0;
                    while (offset < bytes.length) {
                        long count = api.lib.write(file, buffer.share(offset),
                                new NativeLong(bytes.length - offset)).longValue();
                        if (count < 0) {
                            int error = Native.getLastError();
                            if (error == 4) continue;
                            throw api.error("write file", error);
                        }
                        if (count == 0) throw new IOException("workspace write made no progress");
                        offset += count;
                    }
                }
                // Replace the entry, not its target: even a late symlink/hardlink swap
                // cannot truncate an external file. Both paths use the held parent fd.
                if (api.lib.renameat(fd, temporary, fd, name) < 0) {
                    throw api.error("replace file", Native.getLastError());
                }
            } finally {
                api.lib.close(file);
                api.lib.unlinkat(fd, temporary, 0);
            }
        }

        private static void requireName(String name) {
            if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")
                    || name.indexOf('\0') >= 0 || name.equals(".") || name.equals("..")) {
                throw new IllegalArgumentException("a single file name is required");
            }
        }

        @Override
        public void close() {
            if (fd >= 0) {
                api.lib.close(fd);
                fd = -1;
            }
        }
    }

    public interface LibC extends Library {
        int open(String path, int flags, Object... mode);
        int openat(int parent, String path, int flags, Object... mode);
        int mkdirat(int parent, String path, int mode);
        int renameat(int sourceParent, String source, int targetParent, String target);
        int unlinkat(int parent, String path, int flags);
        NativeLong read(int file, byte[] buffer, NativeLong size);
        NativeLong write(int file, Pointer buffer, NativeLong size);
        int fstat(int file, Pointer stat);
        int fstat64(int file, Pointer stat);
        int close(int file);
    }

    private static final class NativeApi {
        private final LibC lib;
        private final int noFollow, directory, closeOnExec, create, exclusive, nonBlock;

        private NativeApi() {
            boolean mac = Platform.isMac();
            boolean linuxArm = Platform.isLinux() && Platform.isARM();
            // Linux arch/arm64/include/uapi/asm/fcntl.h overrides the generic flags.
            noFollow = mac ? 0x100 : linuxArm ? 0x8000 : 0x20000;
            directory = mac ? 0x100000 : linuxArm ? 0x4000 : 0x10000;
            closeOnExec = mac ? 0x1000000 : 0x80000;
            create = mac ? 0x200 : 0x40;
            exclusive = mac ? 0x800 : 0x80;
            nonBlock = mac ? 4 : 0x800;
            lib = Native.load(Platform.C_LIBRARY_NAME, LibC.class,
                    Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));
        }

        private static NativeApi instance() throws IOException {
            if ((!Platform.isLinux() && !Platform.isMac()) || NativeLong.SIZE != 8
                    || (!Platform.isIntel() && !Platform.isARM())) {
                throw new IOException("secure workspace file I/O requires x86_64/aarch64 Linux or macOS; no unsafe fallback");
            }
            try {
                return Holder.INSTANCE;
            } catch (LinkageError e) {
                throw new IOException("secure workspace native file API unavailable; no unsafe fallback", e);
            }
        }

        private static class Holder {
            private static final NativeApi INSTANCE = new NativeApi();
        }

        private int directoryFlags() {
            return directory | noFollow | closeOnExec;
        }

        private int openDirectory(String path) throws IOException {
            int fd = lib.open(path, directoryFlags(), 0);
            if (fd < 0) throw error("open trusted directory", Native.getLastError());
            return fd;
        }

        private void requireRegularFile(int fd) throws IOException {
            // Known 64-bit stat ABIs only: Darwin stat64 st_mode at byte 4 (uint16),
            // Linux x86_64 at byte 24, Linux aarch64 at byte 16 (uint32).
            // Layouts: Darwin SDK sys/stat.h; glibc sysdeps/unix/sysv/linux/
            // {x86/bits/struct_stat.h,bits/struct_stat.h}; musl arch/*/bits/stat.h.
            // Use fstat on the opened descriptor, never a pathname type check.
            try (Memory stat = new Memory(512)) {
                boolean mac = Platform.isMac();
                int result = mac ? lib.fstat64(fd, stat) : lib.fstat(fd, stat);
                if (result < 0) throw error("inspect opened file", Native.getLastError());
                int mode = mac ? Short.toUnsignedInt(stat.getShort(4))
                        : stat.getInt(Platform.isARM() ? 16 : 24);
                if ((mode & 0170000) != 0100000) {
                    throw new IOException("workspace reads require a regular file");
                }
            }
        }

        private IOException error(String operation, int errno) {
            return new IOException("secure workspace " + operation + " failed (errno=" + errno + ")");
        }
    }
}
