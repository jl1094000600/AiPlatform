package com.aipal.agent.runtime.workspace;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceManagerTest {

    @Test
    void tenantIdentitiesGetDistinctRoots() {
        WorkspaceFactory factory = new WorkspaceFactory("local", "docker", "img");
        WorkspaceManager manager = new WorkspaceManager(factory);
        WorkspaceFactory.WorkspaceIdentity a = new WorkspaceFactory.WorkspaceIdentity(10L, 1L, 101L, 1L, null);
        WorkspaceFactory.WorkspaceIdentity b = new WorkspaceFactory.WorkspaceIdentity(20L, 2L, 202L, 2L, null);
        Workspace wa = manager.acquire(a);
        Workspace wb = manager.acquire(b);
        try {
            assertNotEquals(wa.workDir(), wb.workDir(), "tenants must not share workspace root");
            assertTrue(wa.workDir().toString().contains("t10"), wa.workDir().toString());
            assertTrue(wb.workDir().toString().contains("t20"), wb.workDir().toString());
        } finally {
            manager.release(a);
            manager.release(b);
        }
    }

    @Test
    void anonymousIdentityRejected() {
        WorkspaceFactory factory = new WorkspaceFactory("local", "docker", "img");
        WorkspaceManager manager = new WorkspaceManager(factory);
        assertThrows(IllegalArgumentException.class,
                () -> manager.acquire(new WorkspaceFactory.WorkspaceIdentity(0L, null, 0L, null, null)));
    }

    @Test
    void releaseClosesExactlyOnce() {
        AtomicInteger closed = new AtomicInteger();
        Workspace stub = new Workspace() {
            @Override
            public java.nio.file.Path workDir() {
                return java.nio.file.Paths.get("target", "ws-mgr-test");
            }

            @Override
            public CommandResult exec(List<String> command, Duration timeout) {
                return CommandResult.success("cmd", "ok", Duration.ofMillis(1));
            }

            @Override
            public void putFile(String relPath, String content) {
            }

            @Override
            public String getFile(String relPath) {
                return "";
            }

            @Override
            public String health() {
                return "READY";
            }

            @Override
            public void close() {
                closed.incrementAndGet();
            }
        };
        WorkspaceFactory factory = new WorkspaceFactory("local", "docker", "img") {
            @Override
            public Workspace openToolWorkspace(WorkspaceFactory.WorkspaceIdentity identity) {
                return stub;
            }
        };
        WorkspaceManager manager = new WorkspaceManager(factory);
        WorkspaceFactory.WorkspaceIdentity id = new WorkspaceFactory.WorkspaceIdentity(1L, 1L, 2L, null, null);
        manager.acquire(id);
        manager.release(id);
        manager.release(id);
        assertEquals(1, closed.get());
    }
}
