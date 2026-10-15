package com.aipal.service;

import com.aipal.dto.AutomationDeployProfileResponse;
import com.aipal.entity.AutomationBuildRun;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.entity.AutomationTestRun;
import com.aipal.mapper.AutomationBuildRunMapper;
import com.aipal.mapper.AutomationGeneratedCodeBatchMapper;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.mapper.AutomationStageRunMapper;
import com.aipal.mapper.AutomationTestRunMapper;
import com.aipal.agent.runtime.workspace.WorkspaceFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * R01 fail-closed gate tests for build/test execution (B02 / G1-G8).
 * Empty commands, zero tests, unparseable reports and path errors must not succeed.
 */
class AutomationBuildTestExecutionServiceTest {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_BLOCKED = "BLOCKED";

    @Test
    void emptyBuildCommandIsBlockedNotSuccess() {
        Harness harness = Harness.withResults(null, null);
        harness.profile.setBuildCommand("");

        AutomationStageRun stage = harness.executeBuild();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        assertTrue(stage.getErrorMessage() != null && !stage.getErrorMessage().isBlank(),
                "blocked empty build command must write an error reason");
    }

    @Test
    void nullBuildCommandIsBlockedNotSuccess() {
        Harness harness = Harness.withResults(null, null);
        harness.profile.setBuildCommand(null);

        AutomationStageRun stage = harness.executeBuild();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        assertFalse(stage.getErrorMessage() == null || stage.getErrorMessage().isBlank());
    }

    @Test
    void emptyTestCommandIsBlockedNotSuccess() {
        Harness harness = Harness.withResults(null, null);
        harness.profile.setTestCommand("");

        AutomationStageRun stage = harness.executeTest();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        assertTrue(stage.getErrorMessage() != null && !stage.getErrorMessage().isBlank());
    }

    @Test
    void zeroTestsExitZeroIsBlockedNotSuccess() {
        Harness harness = Harness.withResults(null, success(
                "$ mvn test\nTests run: 0, Failures: 0, Errors: 0, Skipped: 0"));
        harness.installValidWorkDir();

        AutomationStageRun stage = harness.executeTest();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        AutomationTestRun run = harness.testRun.get();
        assertNotNull(run);
        assertEquals(STATUS_BLOCKED, run.getStatus());
        assertEquals(0, run.getTotalCount());
        assertTrue(stage.getErrorMessage() != null && stage.getErrorMessage().toLowerCase().contains("zero"),
                "zero-test gate must explain zero tests: " + stage.getErrorMessage());
    }

    @Test
    void mavenErrorsWithExitZeroAreBlockedNotSuccess() {
        // A01: Errors>0 and Failures=0 and exit=0 must not authorize success
        Harness harness = Harness.withResults(null, success(
                "$ mvn test\nTests run: 3, Failures: 0, Errors: 2, Skipped: 0"));
        harness.installValidWorkDir();

        AutomationStageRun stage = harness.executeTest();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        AutomationTestRun run = harness.testRun.get();
        assertNotNull(run);
        assertEquals(STATUS_BLOCKED, run.getStatus());
        assertTrue(run.getFailedCount() != null && run.getFailedCount() > 0,
                "errors must count as failures: failed=" + run.getFailedCount());
        assertTrue(stage.getErrorMessage() != null && !stage.getErrorMessage().isBlank());
    }

    @Test
    void vitestZeroTestsExitZeroIsBlockedNotSuccess() {
        Harness harness = Harness.withResults(null, success("$ npx vitest run\nTests  0 passed (0)"));
        harness.installValidWorkDir();

        AutomationStageRun stage = harness.executeTest();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        assertEquals(0, harness.testRun.get().getTotalCount());
    }

    @Test
    void unparseableTestReportExitZeroIsBlockedNotSuccess() {
        Harness harness = Harness.withResults(null, success("$ npm test\nDone in 1.2s\nAll good"));
        harness.installValidWorkDir();

        AutomationStageRun stage = harness.executeTest();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        assertTrue(stage.getErrorMessage() != null && !stage.getErrorMessage().isBlank());
        assertEquals(STATUS_BLOCKED, harness.testRun.get().getStatus());
    }

    @Test
    void blankTestLogExitZeroIsBlockedNotSuccess() {
        Harness harness = Harness.withResults(null, success(""));
        harness.installValidWorkDir();

        AutomationStageRun stage = harness.executeTest();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
    }

    @Test
    void vitestFailedTestsAreNotSuccess() {
        Harness harness = Harness.withResults(null,
                success("$ npx vitest run\nTests  2 passed | 1 failed (3)"));
        harness.installValidWorkDir();

        AutomationStageRun stage = harness.executeTest();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
    }

    @Test
    void missingGeneratedCodeDirDoesNotFallBackToPlatformRoot() {
        Harness harness = Harness.withResults(success("$ mvn -v\nApache Maven"), null);
        // unique pipeline id so marketDoc/generated-code/pipeline-{id} is absent
        harness.pipeline.setId(987_654_321L);

        AutomationStageRun stage = harness.executeBuild();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        assertNull(harness.buildCommandSeen,
                "must not execute against platform project root when generated dir is missing");
        assertTrue(stage.getErrorMessage() != null && !stage.getErrorMessage().isBlank());
    }

    @Test
    void pathOutsideWorkspaceIsBlockedNotPlatformRoot() {
        Harness harness = Harness.withResults(success("$ mvn -v\nApache Maven"), null);
        harness.profile.setDockerConfig("{\"buildContext\":\"C:\\\\Windows\\\\System32\"}");

        AutomationStageRun stage = harness.executeBuild();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        assertNull(harness.buildCommandSeen, "invalid path must not fall back to host execution");
        assertTrue(stage.getErrorMessage() != null && !stage.getErrorMessage().isBlank());
    }

    @Test
    void corruptDockerConfigJsonIsBlockedNotPlatformRoot() {
        Harness harness = Harness.withResults(success("$ mvn -v\nApache Maven"), null);
        harness.profile.setDockerConfig("{not-json");

        AutomationStageRun stage = harness.executeBuild();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        assertNull(harness.buildCommandSeen);
    }

    @Test
    void parseFailureOnTestPathIsBlocked() {
        Harness harness = Harness.withResults(null, success("$ npm test\nTests run: 1"));
        harness.profile.setDockerConfig("{\"buildContext\":\"C:\\\\Windows\\\\System32\"}");

        AutomationStageRun stage = harness.executeTest();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        assertNull(harness.testCommandSeen, "path failure must prevent test command execution");
    }

    @Test
    void validReportWithPassingTestsSucceeds() {
        Harness harness = Harness.withResults(null, success(
                "$ mvn test\nTests run: 3, Failures: 0, Errors: 0, Skipped: 1"));
        harness.installValidWorkDir();

        AutomationStageRun stage = harness.executeTest();

        assertEquals(STATUS_SUCCESS, stage.getStatus());
        AutomationTestRun run = harness.testRun.get();
        assertNotNull(run);
        assertEquals(STATUS_SUCCESS, run.getStatus());
        assertEquals(3, run.getTotalCount());
        assertEquals(2, run.getPassedCount());
        assertEquals(0, run.getFailedCount());
        assertEquals(1, run.getSkippedCount());
    }

    @Test
    void vitestPassingReportSucceeds() {
        Harness harness = Harness.withResults(null, success("$ npx vitest run\nTests  2 passed (2)"));
        harness.installValidWorkDir();

        AutomationStageRun stage = harness.executeTest();

        assertEquals(STATUS_SUCCESS, stage.getStatus());
        assertEquals(2, harness.testRun.get().getTotalCount());
    }

    @Test
    void successfulBuildWithWorkDirSucceeds() {
        Harness harness = Harness.withResults(success("$ mvn -q compile\nBUILD SUCCESS"), null);
        harness.installValidWorkDir();

        AutomationStageRun stage = harness.executeBuild();

        assertEquals(STATUS_SUCCESS, stage.getStatus());
        assertEquals(STATUS_SUCCESS, harness.buildRun.get().getStatus());
        assertNotNull(harness.buildCommandSeen);
    }

    @Test
    void buildCommandFailureIsBlocked() {
        Harness harness = Harness.withResults(
                failure(1, "$ mvn test\nBUILD FAILURE", "Command failed with exit code 1"), null);
        harness.installValidWorkDir();

        AutomationStageRun stage = harness.executeBuild();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
    }

    @Test
    void testCommandNonZeroExitIsBlockedEvenWithParsedCounts() {
        Harness harness = Harness.withResults(null,
                failure(1, "$ mvn test\nTests run: 2, Failures: 2, Errors: 0, Skipped: 0",
                        "Command failed with exit code 1"));
        harness.installValidWorkDir();

        AutomationStageRun stage = harness.executeTest();

        assertEquals(STATUS_BLOCKED, stage.getStatus());
        assertEquals(2, harness.testRun.get().getFailedCount());
    }

    @Test
    void workspaceFactoryMissingIsBlockedNotHostFallback() throws Exception {
        AutomationDeployProfileResponse profile = new AutomationDeployProfileResponse();
        profile.setTestCommand("mvn test");
        Path sandbox = Paths.get("target", "r01-gate-tests", "ws-missing-factory").toAbsolutePath().normalize();
        Files.createDirectories(sandbox);
        profile.setDockerConfig("{\"buildContext\":\"target/r01-gate-tests/ws-missing-factory\"}");

        AutomationPipeline pipeline = new AutomationPipeline();
        pipeline.setId(42L);
        pipeline.setDeployProfileSnapshot("{}");
        AutomationStageRun stage = newStage("test_execution", "Test Execution");

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
                null);

        AutomationStageRun result = service.executeTest(pipeline, stage);

        assertEquals(STATUS_BLOCKED, result.getStatus());
        assertNotNull(result.getErrorMessage(), "workspace-unavailable must record a reason");
        String error = result.getErrorMessage().toLowerCase();
        assertTrue(error.contains("workspace") || error.contains("isolat"),
                "error must mention workspace/isolation failure, not a host-command excuse: " + result.getErrorMessage());
    }

    @Test
    void workspaceOpenFailureIsBlockedNotSilentHostSuccess() throws Exception {
        WorkspaceFactory factory = mock(WorkspaceFactory.class);
        when(factory.openLocal(any())).thenThrow(new IllegalStateException("docker daemon unavailable"));

        AutomationDeployProfileResponse profile = new AutomationDeployProfileResponse();
        profile.setTestCommand("mvn test");
        Path sandbox = Paths.get("target", "r01-gate-tests", "ws-open-fail").toAbsolutePath().normalize();
        Files.createDirectories(sandbox);
        profile.setDockerConfig("{\"buildContext\":\"target/r01-gate-tests/ws-open-fail\"}");

        AutomationPipeline pipeline = new AutomationPipeline();
        pipeline.setId(43L);
        pipeline.setDeployProfileSnapshot("{}");
        AutomationStageRun stage = newStage("test_execution", "Test Execution");

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

        AutomationStageRun result = service.executeTest(pipeline, stage);

        assertEquals(STATUS_BLOCKED, result.getStatus());
        assertNotNull(result.getErrorMessage());
        String error = result.getErrorMessage().toLowerCase();
        assertTrue(error.contains("workspace") || error.contains("fall back") || error.contains("isolat"),
                "workspace failure must not silently degrade to host success: " + result.getErrorMessage());
    }

    private static AutomationStageRun newStage(String key, String name) {
        AutomationStageRun stage = new AutomationStageRun();
        stage.setId(10L);
        stage.setPipelineId(1L);
        stage.setStageKey(key);
        stage.setStageName(name);
        stage.setStatus("QUEUED");
        return stage;
    }

    private static AutomationBuildTestExecutionService.CommandResult success(String log) {
        return AutomationBuildTestExecutionService.CommandResult.success(log);
    }

    private static AutomationBuildTestExecutionService.CommandResult failure(int exitCode, String log, String error) {
        return AutomationBuildTestExecutionService.CommandResult.failure(exitCode, log, error);
    }

    /**
     * Test harness with stubbed command execution so gate logic can be asserted
     * without depending on a local toolchain.
     */
    private static final class Harness {
        final AutomationPipeline pipeline = new AutomationPipeline();
        final AutomationStageRun buildStage = newStage("build_compile", "Build & Compile");
        final AutomationStageRun testStage = newStage("test_execution", "Test Execution");
        final AutomationDeployProfileResponse profile = new AutomationDeployProfileResponse();
        final AtomicReference<AutomationBuildRun> buildRun = new AtomicReference<>();
        final AtomicReference<AutomationTestRun> testRun = new AtomicReference<>();

        volatile String buildCommandSeen;
        volatile String testCommandSeen;

        private final AutomationBuildTestExecutionService.CommandResult buildResult;
        private final AutomationBuildTestExecutionService.CommandResult testResult;
        private final AutomationBuildTestExecutionService service;

        private Harness(AutomationBuildTestExecutionService.CommandResult buildResult,
                        AutomationBuildTestExecutionService.CommandResult testResult) {
            this.buildResult = buildResult;
            this.testResult = testResult;
            pipeline.setId(1L);
            pipeline.setDeployProfileSnapshot("{}");
            profile.setBuildCommand("mvn -q compile");
            profile.setTestCommand("mvn test");
            profile.setTimeoutSeconds(30);

            AutomationBuildRunMapper buildRunMapper = mock(AutomationBuildRunMapper.class);
            AutomationTestRunMapper testRunMapper = mock(AutomationTestRunMapper.class);
            AutomationDeployProfileService deployProfileService = mock(AutomationDeployProfileService.class);
            when(deployProfileService.readSnapshot(any())).thenReturn(profile);
            doAnswer(inv -> {
                buildRun.set(inv.getArgument(0));
                return 1;
            }).when(buildRunMapper).updateById(any());
            doAnswer(inv -> {
                testRun.set(inv.getArgument(0));
                return 1;
            }).when(testRunMapper).updateById(any());

            service = new AutomationBuildTestExecutionService(
                    buildRunMapper,
                    testRunMapper,
                    mock(AutomationGeneratedCodeBatchMapper.class),
                    mock(AutomationStageRunMapper.class),
                    mock(AutomationPipelineMapper.class),
                    deployProfileService,
                    new ObjectMapper(),
                    mock(WorkspaceFactory.class)) {
                @Override
                AutomationBuildTestExecutionService.CommandResult runCommand(String command, Path workDir, Integer timeoutSeconds) {
                    buildCommandSeen = command;
                    return buildResult != null
                            ? buildResult
                            : success("$ " + command + "\nBUILD SUCCESS");
                }

                @Override
                AutomationBuildTestExecutionService.CommandResult runTestCommandInWorkspace(String command, Path workDir, Integer timeoutSeconds) {
                    testCommandSeen = command;
                    return testResult != null
                            ? testResult
                            : success("$ " + command + "\nTests run: 1, Failures: 0, Errors: 0, Skipped: 0");
                }
            };
        }

        static Harness withResults(AutomationBuildTestExecutionService.CommandResult buildResult,
                                   AutomationBuildTestExecutionService.CommandResult testResult) {
            return new Harness(buildResult, testResult);
        }

        void installValidWorkDir() {
            String name = "sandbox-" + System.nanoTime();
            Path sandbox = Paths.get("target", "r01-gate-tests", name).toAbsolutePath().normalize();
            try {
                Files.createDirectories(sandbox);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            profile.setDockerConfig("{\"buildContext\":\"target/r01-gate-tests/" + name + "\"}");
        }

        AutomationStageRun executeBuild() {
            return service.executeBuild(pipeline, buildStage);
        }

        AutomationStageRun executeTest() {
            return service.executeTest(pipeline, testStage);
        }
    }
}
