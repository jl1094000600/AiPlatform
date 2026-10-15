package com.aipal.service;

import com.aipal.agent.runtime.workspace.CommandResult;
import com.aipal.agent.runtime.workspace.Workspace;
import com.aipal.agent.runtime.workspace.WorkspaceFactory;
import com.aipal.dto.AutomationDeployProfileResponse;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.mapper.AutomationBuildRunMapper;
import com.aipal.mapper.AutomationGeneratedCodeBatchMapper;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.mapper.AutomationStageRunMapper;
import com.aipal.mapper.AutomationTestRunMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SC01 A02/A03/A04: test-stage execution chain with fake Workspace.
 * Fail-closed profiles must never host-fallback; workspace must always be closed
 * (terminal reclaim) on success, failure and timeout.
 */
class AutomationTestExecutionFailClosedTest {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_BLOCKED = "BLOCKED";

    @Test
    void failClosedProfileBlocksTestExecutionWithoutHostFallback() {
        // production mode + unavailable docker: openLocal is refused, so the test
        // stage must block instead of silently executing on the host.
        WorkspaceFactory factory = new WorkspaceFactory(
                "production", "definitely-not-a-docker-binary-xyz", "img");
        StubWorkspaceProbe probe = new StubWorkspaceProbe();
        AutomationStageRun stage = executeTest(factory, "mvn test", probe);

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        assertNotNull(stage.getErrorMessage());
        String error = stage.getErrorMessage().toLowerCase(Locale.ROOT);
        assertTrue(error.contains("refusing to fall back") || error.contains("fail-closed")
                        || error.contains("workspace"),
                "must record fail-closed workspace reason: " + stage.getErrorMessage());
        assertFalse(probe.execCalled, "test command must not run outside the workspace");
    }

    @Test
    void workspaceExecFailureIsBlockedAndWorkspaceAlwaysClosed() {
        StubWorkspaceProbe probe = new StubWorkspaceProbe();
        probe.execException = new IllegalStateException("container exec failed: docker daemon unavailable");
        WorkspaceFactory factory = overridingFactory(probe);

        AutomationStageRun stage = executeTest(factory, "mvn test", probe);

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        assertTrue(probe.execCalled, "workspace exec must be attempted");
        assertTrue(probe.closed, "workspace must be closed after exec failure (terminal reclaim)");
        assertNotNull(stage.getErrorMessage());
        assertTrue(stage.getErrorMessage().toLowerCase(Locale.ROOT).contains("refusing to fall back"),
                "must not degrade to host execution: " + stage.getErrorMessage());
    }

    @Test
    void workspaceTimeoutIsBlockedAndWorkspaceAlwaysClosed() {
        StubWorkspaceProbe probe = new StubWorkspaceProbe();
        probe.execResult = CommandResult.timedOut("mvn test", "partial log", "", Duration.ofSeconds(1));
        WorkspaceFactory factory = overridingFactory(probe);

        AutomationStageRun stage = executeTest(factory, "mvn test", probe);

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        assertTrue(probe.closed, "workspace must be closed after timeout (terminal reclaim)");
        assertTrue(stage.getErrorMessage() != null
                        && stage.getErrorMessage().toLowerCase(Locale.ROOT).contains("timed out"),
                "timeout must be reported: " + stage.getErrorMessage());
    }

    @Test
    void workspaceSuccessStillClosesWorkspace() {
        StubWorkspaceProbe probe = new StubWorkspaceProbe();
        probe.execResult = CommandResult.success("mvn test",
                "Tests run: 2, Failures: 0, Errors: 0, Skipped: 0", Duration.ofSeconds(1));
        WorkspaceFactory factory = overridingFactory(probe);

        AutomationStageRun stage = executeTest(factory, "mvn test", probe);

        assertEquals(STATUS_SUCCESS, stage.getStatus());
        assertTrue(probe.closed, "workspace must be closed after success (terminal reclaim)");
    }

    private static WorkspaceFactory overridingFactory(StubWorkspaceProbe probe) {
        return new WorkspaceFactory("local", "docker", "img") {
            @Override
            public Workspace openLocal(Path root) {
                probe.opened = true;
                return probe;
            }
        };
    }

    /** Builds a service wired with the given factory and runs the real test path. */
    private static AutomationStageRun executeTest(WorkspaceFactory factory, String testCommand,
                                                  StubWorkspaceProbe probe) {
        AutomationDeployProfileResponse profile = new AutomationDeployProfileResponse();
        profile.setTestCommand(testCommand);
        profile.setTimeoutSeconds(30);
        Path sandbox = Paths.get("target", "sc00-sc01-tests",
                "ws-" + System.nanoTime()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(sandbox);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        profile.setDockerConfig("{\"buildContext\":\"target/sc00-sc01-tests/"
                + sandbox.getFileName() + "\"}");

        AutomationPipeline pipeline = new AutomationPipeline();
        pipeline.setId(77L);
        pipeline.setDeployProfileSnapshot("{}");
        AutomationStageRun stage = new AutomationStageRun();
        stage.setId(11L);
        stage.setPipelineId(77L);
        stage.setStageKey("test_execution");
        stage.setStageName("Test Execution");
        stage.setStatus("QUEUED");

        AutomationDeployProfileService deployProfileService = mock(AutomationDeployProfileService.class);
        when(deployProfileService.readSnapshot(any())).thenReturn(profile);

        AutomationBuildTestExecutionService service = new AutomationBuildTestExecutionService(
                mock(AutomationBuildRunMapper.class),
                mock(AutomationTestRunMapper.class),
                mock(AutomationGeneratedCodeBatchMapper.class),
                mock(AutomationStageRunMapper.class),
                mock(AutomationPipelineMapper.class),
                deployProfileService,
                new ObjectMapper(),
                factory);

        return service.executeTest(pipeline, stage);
    }

    /** Records whether the workspace was opened/executed/closed; executes a canned result. */
    private static final class StubWorkspaceProbe implements Workspace {
        volatile boolean opened;
        volatile boolean execCalled;
        volatile boolean closed;
        volatile RuntimeException execException;
        volatile CommandResult execResult = CommandResult.success("mvn test",
                "Tests run: 1, Failures: 0, Errors: 0, Skipped: 0", Duration.ofMillis(1));

        @Override
        public Path workDir() {
            return Paths.get("target");
        }

        @Override
        public CommandResult exec(List<String> command, Duration timeout) {
            execCalled = true;
            if (execException != null) {
                throw execException;
            }
            return execResult;
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
            return closed ? "CLOSED" : "READY";
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
