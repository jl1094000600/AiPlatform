package com.aipal.service;

import com.aipal.dto.AutomationDeployProfileResponse;
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
    private static final Pattern VITEST_TESTS = Pattern.compile("Tests\\s+(\\d+)\\s+passed(?:\\s*\\((\\d+)\\))?", Pattern.CASE_INSENSITIVE);

    private final AutomationBuildRunMapper buildRunMapper;
    private final AutomationTestRunMapper testRunMapper;
    private final AutomationGeneratedCodeBatchMapper generatedCodeBatchMapper;
    private final AutomationStageRunMapper stageRunMapper;
    private final AutomationPipelineMapper pipelineMapper;
    private final AutomationDeployProfileService deployProfileService;
    private final ObjectMapper objectMapper;

    public boolean isBuildOrTestStage(String stageKey) {
        return "build_compile".equals(stageKey) || "test_execution".equals(stageKey);
    }

    public AutomationStageRun executeBuild(AutomationPipeline pipeline, AutomationStageRun stage) {
        return executeBuild(pipeline, stage, latestCodeBatchId(pipeline.getId()));
    }

    public AutomationStageRun executeBuild(AutomationPipeline pipeline, AutomationStageRun stage, Long generatedCodeBatchId) {
        AutomationDeployProfileResponse profile = deployProfileService.readSnapshot(pipeline.getDeployProfileSnapshot());
        String command = profile == null ? null : profile.getBuildCommand();
        int timeoutSeconds = profile == null || profile.getTimeoutSeconds() == null ? 600 : profile.getTimeoutSeconds();
        Path workDir = resolveWorkDir(pipeline, profile);
        LocalDateTime start = LocalDateTime.now();
        AutomationBuildRun run = new AutomationBuildRun();
        run.setPipelineId(pipeline.getId());
        run.setStageRunId(stage.getId());
        run.setGeneratedCodeBatchId(generatedCodeBatchId);
        run.setStatus(STATUS_RUNNING);
        run.setCommandText(command);
        run.setWorkDir(workDir.toString());
        run.setStartTime(start);
        run.setCreateTime(start);
        run.setUpdateTime(start);
        buildRunMapper.insert(run);
        startStage(pipeline, stage, start);

        CommandResult result = isBlank(command)
                ? CommandResult.success("No build command configured; build stage skipped.")
                : runCommand(command, workDir, timeoutSeconds);
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
        Path workDir = resolveWorkDir(pipeline, profile);
        LocalDateTime start = LocalDateTime.now();
        AutomationTestRun run = new AutomationTestRun();
        run.setPipelineId(pipeline.getId());
        run.setStageRunId(stage.getId());
        run.setGeneratedCodeBatchId(generatedCodeBatchId);
        run.setStatus(STATUS_RUNNING);
        run.setCommandText(command);
        run.setWorkDir(workDir.toString());
        run.setStartTime(start);
        run.setCreateTime(start);
        run.setUpdateTime(start);
        testRunMapper.insert(run);
        startStage(pipeline, stage, start);

        CommandResult result = isBlank(command)
                ? CommandResult.success("No test command configured; test stage skipped.")
                : runCommand(command, workDir, timeoutSeconds);
        TestSummary summary = parseTestSummary(result.log());
        finishTestRun(run, result, summary, start);
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

    private CommandResult runCommand(String command, Path workDir, Integer timeoutSeconds) {
        try {
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

    private TestSummary parseTestSummary(String log) {
        if (isBlank(log)) {
            return new TestSummary(0, 0, 0, 0);
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
        Matcher vitest = VITEST_TESTS.matcher(log);
        if (vitest.find()) {
            int passed = parseInt(vitest.group(1));
            return new TestSummary(passed, passed, 0, 0);
        }
        return new TestSummary(0, 0, 0, 0);
    }

    private Path resolveWorkDir(AutomationPipeline pipeline, AutomationDeployProfileResponse profile) {
        try {
            if (profile != null) {
                JsonNode dockerConfig = readJson(profile.getDockerConfig());
                String configured = dockerConfig.path("buildContext").asText("");
                if (!isBlank(configured)) {
                    return resolveInsideWorkspaceOrGenerated(configured, projectRoot());
                }
            }
            Path codeRoot = latestCodeArtifactRoot(pipeline.getId());
            return codeRoot == null ? projectRoot() : codeRoot;
        } catch (Exception e) {
            return projectRoot();
        }
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

    private record CommandResult(boolean success, int exitCode, String log, String errorMessage) {
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
