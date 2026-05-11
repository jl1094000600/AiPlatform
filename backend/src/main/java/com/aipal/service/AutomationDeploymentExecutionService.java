package com.aipal.service;

import com.aipal.dto.AutomationDeployProfileResponse;
import com.aipal.entity.AutomationDeployRun;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.mapper.AutomationDeployRunMapper;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.mapper.AutomationStageRunMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AutomationDeploymentExecutionService {
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final int MAX_LOG_CHARS = 120_000;

    private final AutomationDeployRunMapper deployRunMapper;
    private final AutomationStageRunMapper stageRunMapper;
    private final AutomationPipelineMapper pipelineMapper;
    private final AutomationDeployProfileService deployProfileService;
    private final ObjectMapper objectMapper;

    public boolean isDeploymentStage(String stageKey) {
        return "build_compile".equals(stageKey)
                || "test_execution".equals(stageKey)
                || "deployment_release".equals(stageKey)
                || "operations_monitoring".equals(stageKey);
    }

    public AutomationStageRun executeStage(AutomationPipeline pipeline, AutomationStageRun stage) {
        if (!isDeploymentStage(stage.getStageKey())) {
            return stage;
        }
        AutomationDeployProfileResponse profile = deployProfileService.readSnapshot(pipeline.getDeployProfileSnapshot());
        if (profile == null) {
            return block(stage, pipeline, "Deploy profile snapshot is missing");
        }

        LocalDateTime start = LocalDateTime.now();
        AutomationDeployRun run = createRun(pipeline, stage, profile, start);
        deployRunMapper.insert(run);

        stage.setStatus(STATUS_RUNNING);
        stage.setStartTime(start);
        stage.setEndTime(null);
        stage.setDurationMs(null);
        stage.setErrorMessage(null);
        stage.setOutputSummary("Running deployment stage: " + stage.getStageName());
        stage.setUpdateTime(start);
        stageRunMapper.updateById(stage);

        pipeline.setStatus(STATUS_RUNNING);
        pipeline.setCurrentStage(stage.getStageKey());
        pipeline.setUpdateTime(start);
        pipelineMapper.updateById(pipeline);

        try {
            StageResult result = switch (stage.getStageKey()) {
                case "build_compile" -> executeConfiguredCommand(profile.getBuildCommand(), "No build command configured", workDir(pipeline, profile));
                case "test_execution" -> executeConfiguredCommand(profile.getTestCommand(), "No test command configured", workDir(pipeline, profile));
                case "deployment_release" -> executeDeployment(pipeline, profile, workDir(pipeline, profile));
                case "operations_monitoring" -> executeHealthCheck(profile);
                default -> StageResult.success("Stage skipped");
            };
            finishRun(run, result, start);
            finishStage(stage, result, start);
        } catch (Exception e) {
            StageResult result = StageResult.failure(-1, "", e.getMessage());
            finishRun(run, result, start);
            finishStage(stage, result, start);
        }
        return stage;
    }

    public List<AutomationDeployRun> listRuns(Long pipelineId) {
        return deployRunMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AutomationDeployRun>()
                .eq(AutomationDeployRun::getPipelineId, pipelineId)
                .orderByDesc(AutomationDeployRun::getCreateTime));
    }

    private AutomationDeployRun createRun(AutomationPipeline pipeline, AutomationStageRun stage,
                                          AutomationDeployProfileResponse profile, LocalDateTime start) {
        AutomationDeployRun run = new AutomationDeployRun();
        run.setPipelineId(pipeline.getId());
        run.setStageRunId(stage.getId());
        run.setDeployProfileId(profile.getId());
        run.setStageKey(stage.getStageKey());
        run.setDeployType(profile.getDeployType());
        run.setEnvironmentName(profile.getEnvironmentName());
        run.setStatus(STATUS_RUNNING);
        run.setProfileSnapshot(pipeline.getDeployProfileSnapshot());
        run.setStartTime(start);
        run.setCreateTime(start);
        run.setUpdateTime(start);
        return run;
    }

    private StageResult executeConfiguredCommand(String command, String emptyMessage, Path workDir) throws Exception {
        if (isBlank(command)) {
            return StageResult.success(emptyMessage);
        }
        return runCommand(shellCommand(command), workDir, 600);
    }

    private StageResult executeDeployment(AutomationPipeline pipeline, AutomationDeployProfileResponse profile, Path workDir) throws Exception {
        if ("JENKINS".equalsIgnoreCase(profile.getDeployType())) {
            return triggerJenkins(pipeline, profile);
        }
        return runDockerDeployment(pipeline, profile, workDir);
    }

    private StageResult runDockerDeployment(AutomationPipeline pipeline, AutomationDeployProfileResponse profile, Path workDir) throws Exception {
        JsonNode config = readJson(profile.getDockerConfig());
        String dockerMode = config.path("dockerMode").asText("BUILD_RUN");
        if ("COMPOSE".equalsIgnoreCase(dockerMode)) {
            String composeFile = config.path("composeFile").asText("docker-compose.yml");
            Path composePath = resolveInsideWorkspaceOrWorkDir(composeFile, workDir);
            return runCommand(List.of("docker", "compose", "-f", composePath.toString(), "up", "-d"),
                    composePath.getParent() == null ? workDir : composePath.getParent(), profile.getTimeoutSeconds());
        }

        String imageName = config.path("imageName").asText("aipal-generated");
        String tag = resolveImageTag(config.path("tagStrategy").asText("PIPELINE_ID"), pipeline);
        String fullImage = imageName.contains(":") ? imageName : imageName + ":" + tag;
        String dockerfile = config.path("dockerfilePath").asText("Dockerfile");
        String context = config.path("buildContext").asText(".");
        Path dockerfilePath = resolveInsideWorkspaceOrWorkDir(dockerfile, workDir);
        Path contextPath = resolveInsideWorkspaceOrWorkDir(context, workDir);

        List<String> logs = new ArrayList<>();
        StageResult build = runCommand(List.of("docker", "build", "-f", dockerfilePath.toString(), "-t", fullImage, contextPath.toString()),
                contextPath, profile.getTimeoutSeconds());
        logs.add(build.log());
        if (!build.success()) {
            return build.withLog(joinLogs(logs));
        }

        String containerName = config.path("containerName").asText("");
        if (!isBlank(containerName)) {
            StageResult remove = runCommand(List.of("docker", "rm", "-f", containerName), contextPath, 60, true);
            logs.add(remove.log());
        }
        List<String> runArgs = new ArrayList<>(List.of("docker", "run", "-d"));
        if (!isBlank(containerName)) {
            runArgs.add("--name");
            runArgs.add(containerName);
        }
        appendDockerPorts(runArgs, config.path("ports"));
        appendDockerEnv(runArgs, config.path("envVars"));
        runArgs.add(fullImage);
        StageResult run = runCommand(runArgs, contextPath, profile.getTimeoutSeconds());
        logs.add(run.log());
        return run.withLog(joinLogs(logs)).withImage(fullImage, containerName);
    }

    private StageResult triggerJenkins(AutomationPipeline pipeline, AutomationDeployProfileResponse profile) throws Exception {
        JsonNode config = readJson(profile.getJenkinsConfig());
        String jenkinsUrl = trimTrailingSlash(config.path("jenkinsUrl").asText(""));
        String jobName = config.path("jobName").asText("");
        if (isBlank(jenkinsUrl) || isBlank(jobName)) {
            throw new IllegalArgumentException("Jenkins URL and jobName are required");
        }
        RestClient client = RestClient.create(jenkinsUrl);
        Map<String, String> headers = jenkinsHeaders(client, config);
        Map<String, String> params = readStringMap(config.path("parametersJson").asText(""));
        params.putIfAbsent("PIPELINE_ID", String.valueOf(pipeline.getId()));
        params.putIfAbsent("ENVIRONMENT", profile.getEnvironmentName());
        if (!isBlank(config.path("buildToken").asText(""))) {
            params.put("token", config.path("buildToken").asText(""));
        }
        String jobPath = jenkinsJobPath(jobName);
        String query = toQuery(params);
        RestClient.RequestBodySpec request = client.post().uri(jobPath + "/buildWithParameters" + (query.isBlank() ? "" : "?" + query));
        headers.forEach(request::header);
        ResponseEntity<Void> response = request.retrieve().toBodilessEntity();
        String queueUrl = response.getHeaders().getFirst("Location");
        int pollSeconds = Math.max(2, config.path("pollIntervalSeconds").asInt(5));
        int timeoutSeconds = Math.max(30, profile.getTimeoutSeconds() == null ? 600 : profile.getTimeoutSeconds());
        JenkinsBuild build = waitForJenkinsBuild(client, headers, queueUrl, pollSeconds, timeoutSeconds);
        String log = "Jenkins job triggered: " + jobName + "\n"
                + "Queue: " + (queueUrl == null ? "-" : queueUrl) + "\n"
                + "Build: " + (build.url() == null ? "-" : build.url()) + "\n"
                + "Result: " + build.result();
        return "SUCCESS".equalsIgnoreCase(build.result())
                ? StageResult.success(log).withJenkins(build.number(), build.url())
                : StageResult.failure(1, log, "Jenkins build result: " + build.result()).withJenkins(build.number(), build.url());
    }

    private StageResult executeHealthCheck(AutomationDeployProfileResponse profile) {
        String url = profile.getHealthCheckUrl();
        if (isBlank(url)) {
            return StageResult.success("No health check URL configured");
        }
        long start = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = RestClient.create()
                    .get()
                    .uri(url)
                    .retrieve()
                    .toEntity(String.class);
            int elapsed = (int) (System.currentTimeMillis() - start);
            int statusCode = response.getStatusCode().value();
            String message = "Health check " + statusCode + " in " + elapsed + "ms";
            boolean success = statusCode >= 200 && statusCode < 400;
            return new StageResult(success, success ? 0 : statusCode, message, success ? null : message,
                    null, null, null, null, statusCode, elapsed, message);
        } catch (Exception e) {
            int elapsed = (int) (System.currentTimeMillis() - start);
            return new StageResult(false, -1, e.getMessage(), e.getMessage(),
                    null, null, null, null, null, elapsed, e.getMessage());
        }
    }

    private void finishRun(AutomationDeployRun run, StageResult result, LocalDateTime start) {
        LocalDateTime end = LocalDateTime.now();
        run.setStatus(result.success() ? STATUS_SUCCESS : STATUS_BLOCKED);
        run.setCommandLog(limit(result.log()));
        run.setExitCode(result.exitCode());
        run.setImageName(result.imageName());
        run.setContainerName(result.containerName());
        run.setJenkinsBuildNumber(result.jenkinsBuildNumber());
        run.setJenkinsBuildUrl(result.jenkinsBuildUrl());
        run.setHealthStatusCode(result.healthStatusCode());
        run.setHealthResponseMs(result.healthResponseMs());
        run.setHealthMessage(limitShort(result.healthMessage()));
        run.setErrorMessage(limitShort(result.errorMessage()));
        run.setEndTime(end);
        run.setDurationMs((int) Duration.between(start, end).toMillis());
        run.setUpdateTime(end);
        deployRunMapper.updateById(run);
    }

    private void finishStage(AutomationStageRun stage, StageResult result, LocalDateTime start) {
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

    private AutomationStageRun block(AutomationStageRun stage, AutomationPipeline pipeline, String message) {
        LocalDateTime now = LocalDateTime.now();
        stage.setStatus(STATUS_BLOCKED);
        stage.setErrorMessage(message);
        stage.setOutputSummary(stage.getStageName() + " failed: " + message);
        stage.setEndTime(now);
        stage.setUpdateTime(now);
        stageRunMapper.updateById(stage);
        pipeline.setStatus(STATUS_BLOCKED);
        pipeline.setCurrentStage(stage.getStageKey());
        pipeline.setUpdateTime(now);
        pipelineMapper.updateById(pipeline);
        return stage;
    }

    private StageResult runCommand(List<String> command, Path workDir, Integer timeoutSeconds) throws Exception {
        return runCommand(command, workDir, timeoutSeconds, false);
    }

    private StageResult runCommand(List<String> command, Path workDir, Integer timeoutSeconds, boolean ignoreFailure) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (log.length() < MAX_LOG_CHARS) {
                    log.append(line).append('\n');
                }
            }
        }
        boolean finished = process.waitFor(Math.max(10, timeoutSeconds == null ? 600 : timeoutSeconds), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return StageResult.failure(-1, log + "\nCommand timed out", "Command timed out");
        }
        int exitCode = process.exitValue();
        String commandText = String.join(" ", command);
        String fullLog = "$ " + commandText + "\n" + log;
        if (exitCode == 0 || ignoreFailure) {
            return new StageResult(true, exitCode, fullLog, null, null, null, null, null, null, null, null);
        }
        return StageResult.failure(exitCode, fullLog, "Command failed with exit code " + exitCode);
    }

    private List<String> shellCommand(String command) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return windows ? List.of("cmd.exe", "/c", command) : List.of("sh", "-c", command);
    }

    private Path workDir(AutomationPipeline pipeline, AutomationDeployProfileResponse profile) throws IOException {
        JsonNode dockerConfig = readJson(profile.getDockerConfig());
        String configured = dockerConfig.path("buildContext").asText("");
        if (!isBlank(configured)) {
            return resolveInsideWorkspaceOrWorkDir(configured, projectRoot());
        }
        Path codeRoot = latestCodeArtifactRoot(pipeline.getId());
        return codeRoot == null ? projectRoot() : codeRoot;
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

    private Path resolveInsideWorkspaceOrWorkDir(String value, Path workDir) throws IOException {
        Path raw = Paths.get(value);
        Path resolved = raw.isAbsolute() ? raw : workDir.resolve(value);
        Path normalized = resolved.toAbsolutePath().normalize();
        Path workspace = projectRoot();
        Path allowedGenerated = Paths.get("marketDoc", "generated-code").toAbsolutePath().normalize();
        if (!normalized.startsWith(workspace) && !normalized.startsWith(allowedGenerated)) {
            throw new IllegalArgumentException("Path must stay inside workspace or generated code artifacts: " + value);
        }
        return normalized;
    }

    private Path projectRoot() {
        return Paths.get("").toAbsolutePath().normalize();
    }

    private JsonNode readJson(String json) throws IOException {
        return isBlank(json) ? objectMapper.createObjectNode() : objectMapper.readTree(json);
    }

    private void appendDockerPorts(List<String> args, JsonNode ports) {
        if (ports.isTextual() && !ports.asText("").isBlank()) {
            for (String port : ports.asText("").split(",")) {
                args.add("-p");
                args.add(port.trim());
            }
        } else if (ports.isArray()) {
            ports.forEach(port -> {
                if (!port.asText("").isBlank()) {
                    args.add("-p");
                    args.add(port.asText());
                }
            });
        }
    }

    private void appendDockerEnv(List<String> args, JsonNode envVars) {
        if (!envVars.isObject()) {
            return;
        }
        envVars.fields().forEachRemaining(entry -> {
            args.add("-e");
            args.add(entry.getKey() + "=" + entry.getValue().asText(""));
        });
    }

    private String resolveImageTag(String strategy, AutomationPipeline pipeline) {
        if ("TIMESTAMP".equalsIgnoreCase(strategy)) {
            return String.valueOf(System.currentTimeMillis());
        }
        return "pipeline-" + pipeline.getId();
    }

    private Map<String, String> jenkinsHeaders(RestClient client, JsonNode config) {
        Map<String, String> headers = new LinkedHashMap<>();
        String username = config.path("username").asText("");
        String apiToken = config.path("apiToken").asText("");
        if (!isBlank(username) && !isBlank(apiToken)) {
            String basic = Base64.getEncoder().encodeToString((username + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
            headers.put("Authorization", "Basic " + basic);
        }
        try {
            RestClient.RequestHeadersSpec<?> request = client.get().uri("/crumbIssuer/api/json");
            headers.forEach(request::header);
            String body = request.retrieve().body(String.class);
            JsonNode crumb = objectMapper.readTree(body);
            String field = crumb.path("crumbRequestField").asText("");
            String value = crumb.path("crumb").asText("");
            if (!isBlank(field) && !isBlank(value)) {
                headers.put(field, value);
            }
        } catch (Exception ignored) {
            // Jenkins without CSRF crumb support can still accept token/basic auth.
        }
        return headers;
    }

    private JenkinsBuild waitForJenkinsBuild(RestClient client, Map<String, String> headers,
                                             String queueUrl, int pollSeconds, int timeoutSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        String buildUrl = null;
        Integer buildNumber = null;
        while (System.currentTimeMillis() < deadline && buildUrl == null && !isBlank(queueUrl)) {
            Thread.sleep(pollSeconds * 1000L);
            RestClient.RequestHeadersSpec<?> request = RestClient.create(trimTrailingSlash(queueUrl)).get().uri("/api/json");
            headers.forEach(request::header);
            JsonNode queue = objectMapper.readTree(request.retrieve().body(String.class));
            if (!queue.path("executable").isMissingNode()) {
                buildUrl = queue.path("executable").path("url").asText(null);
                buildNumber = queue.path("executable").path("number").isNumber()
                        ? queue.path("executable").path("number").asInt() : null;
            }
        }
        if (isBlank(buildUrl)) {
            throw new IllegalStateException("Timed out waiting for Jenkins build number");
        }
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(pollSeconds * 1000L);
            RestClient.RequestHeadersSpec<?> request = RestClient.create(trimTrailingSlash(buildUrl)).get().uri("/api/json");
            headers.forEach(request::header);
            JsonNode build = objectMapper.readTree(request.retrieve().body(String.class));
            if (!build.path("building").asBoolean(false)) {
                return new JenkinsBuild(buildNumber, buildUrl, build.path("result").asText("UNKNOWN"));
            }
        }
        throw new IllegalStateException("Timed out waiting for Jenkins build result");
    }

    private String jenkinsJobPath(String jobName) {
        String[] parts = jobName.split("/");
        StringBuilder path = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                path.append("/job/").append(urlEncode(part));
            }
        }
        return path.isEmpty() ? "/job/" + urlEncode(jobName) : path.toString();
    }

    private Map<String, String> readStringMap(String json) {
        if (isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            Map<String, String> result = new LinkedHashMap<>();
            if (node.isObject()) {
                node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText("")));
            }
            return result;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String toQuery(Map<String, String> params) {
        List<String> parts = new ArrayList<>();
        params.forEach((key, value) -> {
            if (!isBlank(value)) {
                parts.add(urlEncode(key) + "=" + urlEncode(value));
            }
        });
        return String.join("&", parts);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String joinLogs(List<String> logs) {
        return String.join("\n\n", logs);
    }

    private String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String limit(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_LOG_CHARS ? value : value.substring(0, MAX_LOG_CHARS);
    }

    private String limitShort(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record JenkinsBuild(Integer number, String url, String result) {
    }

    private record StageResult(boolean success, int exitCode, String log, String errorMessage,
                               String imageName, String containerName, Integer jenkinsBuildNumber,
                               String jenkinsBuildUrl, Integer healthStatusCode, Integer healthResponseMs,
                               String healthMessage) {
        static StageResult success(String log) {
            return new StageResult(true, 0, log, null, null, null, null, null, null, null, null);
        }

        static StageResult failure(int exitCode, String log, String errorMessage) {
            return new StageResult(false, exitCode, log, errorMessage, null, null, null, null, null, null, null);
        }

        StageResult withLog(String nextLog) {
            return new StageResult(success, exitCode, nextLog, errorMessage, imageName, containerName,
                    jenkinsBuildNumber, jenkinsBuildUrl, healthStatusCode, healthResponseMs, healthMessage);
        }

        StageResult withImage(String nextImageName, String nextContainerName) {
            return new StageResult(success, exitCode, log, errorMessage, nextImageName, nextContainerName,
                    jenkinsBuildNumber, jenkinsBuildUrl, healthStatusCode, healthResponseMs, healthMessage);
        }

        StageResult withJenkins(Integer nextBuildNumber, String nextBuildUrl) {
            return new StageResult(success, exitCode, log, errorMessage, imageName, containerName,
                    nextBuildNumber, nextBuildUrl, healthStatusCode, healthResponseMs, healthMessage);
        }
    }
}
