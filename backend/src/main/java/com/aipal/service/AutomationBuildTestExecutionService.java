package com.aipal.service;

import com.aipal.dto.AutomationDeployProfileResponse;
import com.aipal.agent.runtime.workspace.Workspace;
import com.aipal.agent.runtime.workspace.WorkspaceFactory;
import com.aipal.entity.AutomationBuildRun;
import com.aipal.entity.AutomationGeneratedCodeBatch;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.entity.AutomationTestRun;
import com.aipal.mapper.AutomationBuildRunMapper;
import com.aipal.mapper.AutomationGeneratedCodeBatchMapper;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.mapper.AutomationStageRunMapper;
import com.aipal.mapper.AutomationTestRunMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AutomationBuildTestExecutionService {
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final int MAX_LOG_CHARS = 120_000;
    private static final Pattern MAVEN_TESTS = Pattern.compile("Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+)");
    private static final Pattern VITEST_PASSED = Pattern.compile("Tests\\s+(\\d+)\\s+passed", Pattern.CASE_INSENSITIVE);
    private static final Pattern VITEST_FAILED = Pattern.compile("(\\d+)\\s+failed", Pattern.CASE_INSENSITIVE);

    private final AutomationBuildRunMapper buildRunMapper;
    private final AutomationTestRunMapper testRunMapper;
    private final AutomationGeneratedCodeBatchMapper generatedCodeBatchMapper;
    private final AutomationStageRunMapper stageRunMapper;
    private final AutomationPipelineMapper pipelineMapper;
    private final AutomationDeployProfileService deployProfileService;
    private final ObjectMapper objectMapper;
    private final WorkspaceFactory workspaceFactory;

    public boolean isBuildOrTestStage(String stageKey) {
        return com.aipal.common.PipelineStages.BUILD_COMPILE.equals(stageKey)
                || com.aipal.common.PipelineStages.TEST_EXECUTION.equals(stageKey);
    }

    public AutomationStageRun executeBuild(AutomationPipeline pipeline, AutomationStageRun stage) {
        return executeBuild(pipeline, stage, latestCodeBatchId(pipeline.getId()));
    }

    public AutomationStageRun executeBuild(AutomationPipeline pipeline, AutomationStageRun stage, Long generatedCodeBatchId) {
        AutomationDeployProfileResponse profile = deployProfileService.readSnapshot(pipeline.getDeployProfileSnapshot());
        String command = profile == null ? null : profile.getBuildCommand();
        int timeoutSeconds = profile == null || profile.getTimeoutSeconds() == null ? 600 : profile.getTimeoutSeconds();
        LocalDateTime start = LocalDateTime.now();
        AutomationBuildRun run = new AutomationBuildRun();
        run.setPipelineId(pipeline.getId());
        run.setStageRunId(stage.getId());
        run.setGeneratedCodeBatchId(generatedCodeBatchId);
        run.setStatus(STATUS_RUNNING);
        run.setCommandText(command);
        run.setStartTime(start);
        run.setCreateTime(start);
        run.setUpdateTime(start);
        buildRunMapper.insert(run);
        startStage(pipeline, stage, start);

        CommandResult result;
        if (isBlank(command)) {
            result = CommandResult.failure(-1, "",
                    "No build command configured; build stage blocked (fail-closed, empty command cannot succeed)");
        } else {
            try {
                Path workDir = resolveWorkDir(pipeline, profile, generatedCodeBatchId);
                run.setWorkDir(workDir.toString());
                result = runCommandInWorkspace(command, workDir, timeoutSeconds);
            } catch (Exception e) {
                result = CommandResult.failure(-1, "",
                        "Cannot resolve build work directory: " + e.getMessage());
            }
        }
        finishBuildRun(run, result, start);
        finishStage(stage, result, start);
        return stage;
    }

    public AutomationStageRun executeTest(AutomationPipeline pipeline, AutomationStageRun stage) {
        return executeTest(pipeline, stage, latestCodeBatchId(pipeline.getId()));
    }

    public AutomationStageRun executeTest(AutomationPipeline pipeline, AutomationStageRun stage, Long generatedCodeBatchId) {
        AutomationDeployProfileResponse profile = deployProfileService.readSnapshot(pipeline.getDeployProfileSnapshot());
        String command = profile == null ? null : profile.getTestCommand();
        int timeoutSeconds = profile == null || profile.getTimeoutSeconds() == null ? 600 : profile.getTimeoutSeconds();
        LocalDateTime start = LocalDateTime.now();
        AutomationTestRun run = new AutomationTestRun();
        run.setPipelineId(pipeline.getId());
        run.setStageRunId(stage.getId());
        run.setGeneratedCodeBatchId(generatedCodeBatchId);
        run.setStatus(STATUS_RUNNING);
        run.setCommandText(command);
        run.setStartTime(start);
        run.setCreateTime(start);
        run.setUpdateTime(start);
        testRunMapper.insert(run);
        startStage(pipeline, stage, start);

        CommandResult result;
        if (isBlank(command)) {
            result = CommandResult.failure(-1, "",
                    "No test command configured; test stage blocked (fail-closed, empty command cannot succeed)");
        } else {
            try {
                Path workDir = resolveWorkDir(pipeline, profile, generatedCodeBatchId);
                run.setWorkDir(workDir.toString());
                result = runTestCommandInWorkspace(command, workDir, timeoutSeconds);
            } catch (Exception e) {
                result = CommandResult.failure(-1, "",
                        "Cannot resolve test work directory: " + e.getMessage());
            }
        }
        // Always parse the report when present so failure counts stay accurate even on non-zero exit.
        TestSummary summary = parseTestSummary(result.log());
        result = gateTestOutcome(result, summary);
        finishTestRun(run, result, summary == null ? new TestSummary(0, 0, 0, 0) : summary, start);
        finishStage(stage, result, start);
        return stage;
    }

    public List<AutomationBuildRun> listBuildRuns(Long pipelineId) {
        return buildRunMapper.selectList(new LambdaQueryWrapper<AutomationBuildRun>()
                .eq(AutomationBuildRun::getPipelineId, pipelineId)
                .orderByDesc(AutomationBuildRun::getCreateTime));
    }

    public List<AutomationTestRun> listTestRuns(Long pipelineId) {
        return testRunMapper.selectList(new LambdaQueryWrapper<AutomationTestRun>()
                .eq(AutomationTestRun::getPipelineId, pipelineId)
                .orderByDesc(AutomationTestRun::getCreateTime));
    }

    private void startStage(AutomationPipeline pipeline, AutomationStageRun stage, LocalDateTime start) {
        stage.setStatus(STATUS_RUNNING);
        stage.setStartTime(start);
        stage.setEndTime(null);
        stage.setDurationMs(null);
        stage.setErrorMessage(null);
        stage.setOutputSummary("Running " + stage.getStageName());
        stage.setUpdateTime(start);
        stageRunMapper.updateById(stage);

        pipeline.setStatus(STATUS_RUNNING);
        pipeline.setCurrentStage(stage.getStageKey());
        pipeline.setUpdateTime(start);
        pipelineMapper.updateById(pipeline);
    }

    private void finishStage(AutomationStageRun stage, CommandResult result, LocalDateTime start) {
        LocalDateTime end = LocalDateTime.now();
        stage.setStatus(result.success() ? STATUS_SUCCESS : STATUS_BLOCKED);
        stage.setOutputSummary(result.success()
                ? stage.getStageName() + " succeeded"
                : stage.getStageName() + " failed: " + limitShort(result.errorMessage()));
        stage.setErrorMessage(result.success() ? null : limitShort(result.errorMessage()));
        stage.setEndTime(end);
        stage.setDurationMs((int) Duration.between(start, end).toMillis());
        stage.setUpdateTime(end);
        stageRunMapper.updateById(stage);
    }

    private void finishBuildRun(AutomationBuildRun run, CommandResult result, LocalDateTime start) {
        LocalDateTime end = LocalDateTime.now();
        run.setStatus(result.success() ? STATUS_SUCCESS : STATUS_BLOCKED);
        run.setExitCode(result.exitCode());
        run.setCommandLog(limit(result.log()));
        run.setErrorMessage(limitShort(result.errorMessage()));
        run.setEndTime(end);
        run.setDurationMs((int) Duration.between(start, end).toMillis());
        run.setUpdateTime(end);
        buildRunMapper.updateById(run);
    }

    private void finishTestRun(AutomationTestRun run, CommandResult result, TestSummary summary, LocalDateTime start) {
        LocalDateTime end = LocalDateTime.now();
        run.setStatus(result.success() ? STATUS_SUCCESS : STATUS_BLOCKED);
        run.setExitCode(result.exitCode());
        run.setTotalCount(summary.total());
        run.setPassedCount(summary.passed());
        run.setFailedCount(summary.failed());
        run.setSkippedCount(summary.skipped());
        run.setCommandLog(limit(result.log()));
        run.setErrorMessage(limitShort(result.errorMessage()));
        run.setEndTime(end);
        run.setDurationMs((int) Duration.between(start, end).toMillis());
        run.setUpdateTime(end);
        testRunMapper.updateById(run);
    }

    CommandResult runCommandInWorkspace(String command, Path workDir, Integer timeoutSeconds) {
        return runTestCommandInWorkspace(command, workDir, timeoutSeconds);
    }

    // package-private so gate unit tests can stub process execution
    CommandResult runCommand(String command, Path workDir, Integer timeoutSeconds) {
        try {
            AutomationCommandGuard.requireSafeCommand(command, "automation command");
            ProcessBuilder builder = new ProcessBuilder(shellCommand(command));
            builder.directory(workDir.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            FutureTask<String> logFuture = new FutureTask<>(() -> readProcessOutput(process));
            Thread.ofVirtual().name("automation-build-log").start(logFuture);
            boolean finished = process.waitFor(Math.max(10, timeoutSeconds == null ? 600 : timeoutSeconds), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                String log = readFutureLog(logFuture);
                return CommandResult.failure(-1, "$ " + command + "\n" + log + "\nCommand timed out", "Command timed out");
            }
            int exitCode = process.exitValue();
            String log = "$ " + command + "\n" + readFutureLog(logFuture);
            return exitCode == 0
                    ? CommandResult.success(log)
                    : CommandResult.failure(exitCode, log, "Command failed with exit code " + exitCode);
        } catch (Exception e) {
            return CommandResult.failure(-1, "$ " + command + "\n" + e.getMessage(), e.getMessage());
        }
    }

    /**
     * Execute test commands inside the Workspace sandbox. Fail closed when the workspace
     * cannot be created or used — never silently fall back to a host process (B02/G8).
     */
    // package-private so gate unit tests can stub process execution
    CommandResult runTestCommandInWorkspace(String command, Path workDir, Integer timeoutSeconds) {
        if (workspaceFactory == null) {
            return CommandResult.failure(-1, "$ " + command,
                    "Workspace factory unavailable; refusing to execute tests outside workspace isolation (fail-closed)");
        }
        try {
            AutomationCommandGuard.requireSafeCommand(command, "testCommand");
            Workspace workspace = workspaceFactory.open(workDir);
            try {
                com.aipal.agent.runtime.workspace.CommandResult wsResult =
                        workspace.exec(shellCommandForWorkspace(command),
                                Duration.ofSeconds(Math.max(10, timeoutSeconds == null ? 600 : timeoutSeconds)));
                String log = "$ " + command + "\n" + wsResult.stdout()
                        + (wsResult.stderr().isBlank() ? "" : "\n" + wsResult.stderr());
                if (wsResult.timedOut()) {
                    return CommandResult.failure(-1, log + "\nCommand timed out", "Command timed out");
                }
                return wsResult.exitCode() == 0
                        ? CommandResult.success(log)
                        : CommandResult.failure(wsResult.exitCode(), log,
                        "Command failed with exit code " + wsResult.exitCode());
            } finally {
                workspace.close();
            }
        } catch (Exception e) {
            return CommandResult.failure(-1, "$ " + command + "\n" + e.getMessage(),
                    "Workspace test execution failed; refusing to fall back to host process (fail-closed): "
                            + e.getMessage());
        }
    }

    /**
     * Fail-closed test gate: exit code 0 alone is not success. Requires a parseable
     * summary, total > 0 and zero failing tests (G4/G5/G6).
     */
    private CommandResult gateTestOutcome(CommandResult result, TestSummary summary) {
        if (!result.success()) {
            return result;
        }
        if (summary == null) {
            return CommandResult.failure(result.exitCode(), result.log(),
                    "Test report could not be parsed (no recognized test summary in output); failing closed");
        }
        if (summary.total() <= 0) {
            return CommandResult.failure(result.exitCode(), result.log(),
                    "Zero tests were executed or reported; failing closed");
        }
        if (summary.failed() > 0) {
            return CommandResult.failure(result.exitCode(), result.log(),
                    "Test run reported " + summary.failed() + " failing test(s); failing closed");
        }
        return result;
    }

    private TestSummary parseTestSummary(String log) {
        if (isBlank(log)) {
            return null;
        }
        Matcher maven = MAVEN_TESTS.matcher(log);
        TestSummary latest = null;
        while (maven.find()) {
            int total = parseInt(maven.group(1));
            int failures = parseInt(maven.group(2));
            int errors = parseInt(maven.group(3));
            int skipped = parseInt(maven.group(4));
            int failed = failures + errors;
            latest = new TestSummary(total, Math.max(0, total - failed - skipped), failed, skipped);
        }
        if (latest != null) {
            return latest;
        }
        Matcher passed = VITEST_PASSED.matcher(log);
        if (passed.find()) {
            int passedCount = parseInt(passed.group(1));
            int failedCount = 0;
            Matcher failedMatcher = VITEST_FAILED.matcher(log);
            if (failedMatcher.find()) {
                failedCount = parseInt(failedMatcher.group(1));
            }
            return new TestSummary(passedCount + failedCount, passedCount, failedCount, 0);
        }
        return null;
    }

    private Path resolveWorkDir(AutomationPipeline pipeline, AutomationDeployProfileResponse profile,
                                Long generatedCodeBatchId) {
        Path batchRoot = codeRootForBatch(generatedCodeBatchId);
        try {
            if (profile != null) {
                JsonNode dockerConfig = readJson(profile.getDockerConfig());
                String configured = dockerConfig.path("buildContext").asText("");
                if (!isBlank(configured)) {
                    Path base = batchRoot != null ? batchRoot : projectRoot();
                    Path resolved = resolveInsideWorkspaceOrGenerated(configured, base);
                    if (batchRoot != null && !resolved.startsWith(batchRoot)
                            && !resolved.startsWith(Paths.get("marketDoc", "generated-code").toAbsolutePath().normalize())) {
                        throw new IllegalStateException(
                                "buildContext must stay inside the selected code batch artifact directory (fail-closed)");
                    }
                    return resolved;
                }
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to resolve configured buildContext/work directory: " + e.getMessage(), e);
        }
        if (batchRoot == null) {
            throw new IllegalStateException(
                    "Generated code artifact directory is missing for batch; refusing mtime/platform-root fallback (fail-closed)");
        }
        return batchRoot;
    }

    /** F03: bind work dir to the exact generated code batch, never directory mtime. */
    private Path codeRootForBatch(Long generatedCodeBatchId) {
        if (generatedCodeBatchId == null) {
            return null;
        }
        AutomationGeneratedCodeBatch batch = generatedCodeBatchMapper.selectById(generatedCodeBatchId);
        if (batch == null || isBlank(batch.getArtifactPath())) {
            return null;
        }
        Path root = Paths.get(batch.getArtifactPath()).toAbsolutePath().normalize();
        Path generated = Paths.get("marketDoc", "generated-code").toAbsolutePath().normalize();
        if (!root.startsWith(generated)) {
            throw new IllegalStateException("Batch artifact path is outside generated-code root (fail-closed)");
        }
        return Files.isDirectory(root) ? root : null;
    }

    private Long latestCodeBatchId(Long pipelineId) {
        AutomationGeneratedCodeBatch batch = generatedCodeBatchMapper.selectOne(
                new LambdaQueryWrapper<AutomationGeneratedCodeBatch>()
                        .eq(AutomationGeneratedCodeBatch::getPipelineId, pipelineId)
                        .orderByDesc(AutomationGeneratedCodeBatch::getCreateTime)
                        .orderByDesc(AutomationGeneratedCodeBatch::getId)
                        .last("LIMIT 1")
        );
        return batch == null ? null : batch.getId();
    }

    private Path latestCodeArtifactRoot(Long pipelineId) {
        Path root = Paths.get("marketDoc", "generated-code", "pipeline-" + pipelineId).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (var paths = Files.list(root)) {
            return paths.filter(Files::isDirectory)
                    .max((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .orElse(root);
        } catch (IOException e) {
            return root;
        }
    }

    private Path resolveInsideWorkspaceOrGenerated(String value, Path workDir) {
        Path raw = Paths.get(value);
        Path resolved = raw.isAbsolute() ? raw : workDir.resolve(value);
        Path normalized = resolved.toAbsolutePath().normalize();
        Path workspace = projectRoot();
        Path generated = Paths.get("marketDoc", "generated-code").toAbsolutePath().normalize();
        if (!normalized.startsWith(workspace) && !normalized.startsWith(generated)) {
            throw new IllegalArgumentException("Path must stay inside workspace or generated code artifacts: " + value);
        }
        return normalized;
    }

    private JsonNode readJson(String json) throws IOException {
        return isBlank(json) ? objectMapper.createObjectNode() : objectMapper.readTree(json);
    }

    private List<String> shellCommand(String command) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return windows ? List.of("cmd.exe", "/c", command) : List.of("sh", "-c", command);
    }

    /** F02: Linux containers must never receive Windows host shells. */
    List<String> shellCommandForWorkspace(String command) {
        return List.of("sh", "-c", command);
    }

    private String readProcessOutput(Process process) {
        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (log.length() < MAX_LOG_CHARS) {
                    log.append(line).append('\n');
                }
            }
        } catch (IOException e) {
            log.append("Failed to read process output: ").append(e.getMessage());
        }
        return log.toString();
    }

    private String readFutureLog(Future<String> logFuture) {
        try {
            return logFuture.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "";
        }
    }

    private Path projectRoot() {
        return Paths.get("").toAbsolutePath().normalize();
    }

    private int parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String limit(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_LOG_CHARS ? value.substring(0, MAX_LOG_CHARS) + "\n...truncated" : value;
    }

    private String limitShort(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 512 ? value.substring(0, 512) : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // package-private so gate unit tests can build stub results
    record CommandResult(boolean success, int exitCode, String log, String errorMessage) {
        static CommandResult success(String log) {
            return new CommandResult(true, 0, log, null);
        }

        static CommandResult failure(int exitCode, String log, String errorMessage) {
            return new CommandResult(false, exitCode, log, errorMessage);
        }
    }

    private record TestSummary(int total, int passed, int failed, int skipped) {
    }
}
