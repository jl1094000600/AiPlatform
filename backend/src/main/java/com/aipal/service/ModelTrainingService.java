package com.aipal.service;

import com.aipal.dto.ModelTrainingJob;
import com.aipal.dto.ModelTrainingRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModelTrainingService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DEFAULT_MODEL_PATH = "BAAI/bge-m3";
    private static final String DEFAULT_TRAIN_DATA = "bge-m3-training/data/train.jsonl";
    private static final String DEFAULT_OUTPUT_DIR = "bge-m3-training/output/bge-m3-ft";
    private static final String TRAINING_ROOT = "bge-m3-training";

    private final Map<String, ModelTrainingJob> jobs = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ModelTrainingJob> listJobs() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(ModelTrainingJob::getCreateTime).reversed())
                .toList();
    }

    public ModelTrainingJob getJob(String id) {
        ModelTrainingJob job = jobs.get(id);
        if (job == null) {
            throw new IllegalArgumentException("Training job does not exist: " + id);
        }
        refreshMetrics(job);
        return job;
    }

    public String getLogs(String id) {
        ModelTrainingJob job = getJob(id);
        Path logPath = Paths.get(job.getLogPath());
        if (!Files.isRegularFile(logPath)) {
            return "";
        }
        try {
            return Files.readString(logPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read training log: " + e.getMessage(), e);
        }
    }

    public ModelTrainingJob createJob(ModelTrainingRequest request) {
        ModelTrainingRequest normalized = normalizeRequest(request);
        String jobId = "MT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Path runDir = projectRoot().resolve(TRAINING_ROOT).resolve("runs").resolve(jobId).normalize();
        Path logPath = runDir.resolve("train.log");
        Path metricsPath = runDir.resolve("metrics.json");
        try {
            Files.createDirectories(runDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create training run directory: " + e.getMessage(), e);
        }

        ModelTrainingJob job = new ModelTrainingJob();
        job.setId(jobId);
        job.setStatus(STATUS_PENDING);
        job.setModelPath(normalized.getModelPath());
        job.setTrainData(normalized.getTrainData());
        job.setOutputDir(normalized.getOutputDir());
        job.setUnifiedFinetuning(Boolean.TRUE.equals(normalized.getUnifiedFinetuning()));
        job.setDryRun(Boolean.TRUE.equals(normalized.getDryRun()));
        job.setLogPath(logPath.toString());
        job.setMetricsPath(metricsPath.toString());
        job.setCreateTime(LocalDateTime.now());
        jobs.put(jobId, job);

        CompletableFuture.runAsync(() -> runTraining(job, normalized, logPath, metricsPath));
        return job;
    }

    private void runTraining(ModelTrainingJob job, ModelTrainingRequest request, Path logPath, Path metricsPath) {
        job.setStatus(STATUS_RUNNING);
        job.setStartTime(LocalDateTime.now());
        try {
            List<String> command = buildCommand(job.getId(), request, metricsPath);
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(projectRoot().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            Process process = builder.start();
            int exitCode = process.waitFor();
            job.setEndTime(LocalDateTime.now());
            if (exitCode == 0) {
                job.setStatus(STATUS_SUCCESS);
                refreshMetrics(job);
            } else {
                job.setStatus(STATUS_FAILED);
                job.setErrorMessage("Training process exited with code " + exitCode);
            }
        } catch (Exception e) {
            job.setStatus(STATUS_FAILED);
            job.setErrorMessage(e.getMessage());
            job.setEndTime(LocalDateTime.now());
            try {
                Files.writeString(logPath, "\n[AI Platform] Training failed: " + e.getMessage() + "\n",
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // Preserve the task state even if the diagnostic log append fails.
            }
        }
    }

    private List<String> buildCommand(String jobId, ModelTrainingRequest request, Path metricsPath) {
        List<String> command = new ArrayList<>();
        command.add("python");
        command.add(projectRoot().resolve(TRAINING_ROOT).resolve("train_bgem3.py").toString());
        command.add("--job-id");
        command.add(jobId);
        command.add("--model-path");
        command.add(request.getModelPath());
        command.add("--train-data");
        command.add(request.getTrainData());
        command.add("--output-dir");
        command.add(request.getOutputDir());
        command.add("--epochs");
        command.add(String.valueOf(request.getEpochs()));
        command.add("--learning-rate");
        command.add(request.getLearningRate().toPlainString());
        command.add("--query-max-len");
        command.add(String.valueOf(request.getQueryMaxLen()));
        command.add("--passage-max-len");
        command.add(String.valueOf(request.getPassageMaxLen()));
        command.add("--train-group-size");
        command.add(String.valueOf(request.getTrainGroupSize()));
        command.add("--metrics-file");
        command.add(metricsPath.toString());
        if (Boolean.TRUE.equals(request.getUnifiedFinetuning())) {
            command.add("--unified-finetuning");
        }
        if (Boolean.TRUE.equals(request.getDryRun())) {
            command.add("--dry-run");
        }
        if (request.getDevice() != null && !request.getDevice().isBlank()) {
            command.add("--device");
            command.add(request.getDevice().trim());
        }
        return command;
    }

    private ModelTrainingRequest normalizeRequest(ModelTrainingRequest request) {
        ModelTrainingRequest source = request == null ? new ModelTrainingRequest() : request;
        ModelTrainingRequest normalized = new ModelTrainingRequest();
        normalized.setModelPath(blankToDefault(source.getModelPath(), DEFAULT_MODEL_PATH));
        normalized.setTrainData(validateProjectPath(blankToDefault(source.getTrainData(), DEFAULT_TRAIN_DATA), "trainData", false));
        normalized.setOutputDir(validateProjectPath(blankToDefault(source.getOutputDir(), DEFAULT_OUTPUT_DIR), "outputDir", true));
        normalized.setEpochs(source.getEpochs() == null ? 1 : positive(source.getEpochs(), "epochs"));
        normalized.setLearningRate(source.getLearningRate() == null ? new BigDecimal("0.00001") : source.getLearningRate());
        normalized.setQueryMaxLen(source.getQueryMaxLen() == null ? 256 : positive(source.getQueryMaxLen(), "queryMaxLen"));
        normalized.setPassageMaxLen(source.getPassageMaxLen() == null ? 512 : positive(source.getPassageMaxLen(), "passageMaxLen"));
        normalized.setTrainGroupSize(source.getTrainGroupSize() == null ? 4 : positive(source.getTrainGroupSize(), "trainGroupSize"));
        normalized.setUnifiedFinetuning(Boolean.TRUE.equals(source.getUnifiedFinetuning()));
        normalized.setDryRun(Boolean.TRUE.equals(source.getDryRun()));
        normalized.setDevice(source.getDevice());
        return normalized;
    }

    private String validateProjectPath(String value, String fieldName, boolean allowMissing) {
        Path root = projectRoot();
        Path path = root.resolve(value).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException(fieldName + " must stay inside the project root");
        }
        if (!allowMissing && !Files.isRegularFile(path)) {
            throw new IllegalArgumentException(fieldName + " does not exist: " + value);
        }
        return root.relativize(path).toString().replace("\\", "/");
    }

    private int positive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0");
        }
        return value;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Path projectRoot() {
        return Paths.get("").toAbsolutePath().normalize();
    }

    private void refreshMetrics(ModelTrainingJob job) {
        if (job.getMetricsPath() == null) {
            return;
        }
        Path path = Paths.get(job.getMetricsPath());
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            Map<String, Object> metrics = objectMapper.readValue(path.toFile(), new TypeReference<LinkedHashMap<String, Object>>() {});
            job.setMetrics(metrics);
        } catch (IOException ignored) {
            // Metrics are optional; logs remain the source of truth when parsing fails.
        }
    }
}
