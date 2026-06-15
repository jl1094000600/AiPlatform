package com.aipal.service;

import com.aipal.dto.ModelTrainingJob;
import com.aipal.dto.ModelTrainingRequest;
import com.aipal.dto.ModelTrainingDataset;
import com.aipal.dto.ModelTrainingDatasetImportRequest;
import com.aipal.dto.ModelTrainingDatasetMockRequest;
import com.aipal.dto.ModelTrainingDatasetPreview;
import com.aipal.dto.ModelTrainingDatasetSaveRequest;
import com.aipal.entity.AiDataset;
import com.aipal.entity.AiModel;
import com.aipal.mapper.AiDatasetMapper;
import com.aipal.mapper.AiModelMapper;
import com.aipal.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@Slf4j
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
    private static final String TRAINING_DATA_DIR = "data";
    private static final String TRAINING_DATASET_CATEGORY = "model_training";
    private static final String TRAINING_DATASET_FORMAT = "jsonl";

    private final Map<String, ModelTrainingJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService trainingExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired(required = false)
    private AiDatasetMapper datasetMapper;
    @Autowired(required = false)
    private AiModelMapper modelMapper;

    public List<ModelTrainingDataset> listDatasets() {
        List<ModelTrainingDataset> result = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        for (ModelTrainingDataset dataset : listDatabaseDatasets()) {
            result.add(dataset);
            seenPaths.add(dataset.getPath());
        }

        Path dataRoot = trainingDataRoot();
        if (!Files.isDirectory(dataRoot)) {
            return result;
        }
        try (Stream<Path> paths = Files.walk(dataRoot)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .map(this::toDataset)
                    .filter(dataset -> seenPaths.add(dataset.getPath()))
                    .forEach(result::add);
            return result.stream()
                    .sorted(Comparator.comparing(ModelTrainingDataset::getModifiedTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list training datasets: " + e.getMessage(), e);
        }
    }

    public ModelTrainingDataset importDataset(ModelTrainingDatasetImportRequest request) {
        if (request == null || request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("Dataset content is required");
        }
        String fileName = normalizeDatasetFileName(request.getFileName(), "imported-training-data.jsonl");
        Path target = trainingDataRoot().resolve("imported").resolve(fileName).normalize();
        ensureInsideTrainingData(target);
        writeAndValidateDataset(target, request.getContent());
        ModelTrainingDataset dataset = toDataset(target);
        saveDatasetRecord(dataset, request.getFileName(), "imported");
        return dataset;
    }

    public ModelTrainingDatasetPreview previewMockDataset(ModelTrainingDatasetMockRequest request) {
        String topic = request == null || request.getTopic() == null || request.getTopic().isBlank()
                ? "AI Platform"
                : request.getTopic().trim();
        int count = request == null || request.getCount() == null ? 12 : positive(request.getCount(), "count");
        count = Math.min(count, 500);
        String fileName = normalizeDatasetFileName(
                request == null ? null : request.getFileName(),
                "mock-bgem3-training.jsonl"
        );

        String requestedModelCode = request == null ? null : request.getModelCode();
        AiModel model = null;
        String content;
        List<Map<String, Object>> records;
        try {
            model = resolveGenerationModel(requestedModelCode);
            String modelContent = callModelForDataset(model, topic, count);
            records = parseModelDatasetRecords(modelContent, count);
            content = toJsonl(records);
        } catch (RuntimeException e) {
            String modelCode = model == null ? requestedModelCode : model.getModelCode();
            log.error("Model training dataset preview failed. topic={}, count={}, modelCode={}, reason={}",
                    topic, count, modelCode, e.getMessage(), e);
            throw e;
        }

        ModelTrainingDatasetPreview preview = new ModelTrainingDatasetPreview();
        preview.setFileName(fileName);
        preview.setTopic(topic);
        preview.setCount(count);
        preview.setModelCode(model.getModelCode());
        preview.setRecords(records);
        preview.setContent(content);
        return preview;
    }

    public ModelTrainingDataset saveDataset(ModelTrainingDatasetSaveRequest request) {
        if (request == null || request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("Dataset content is required");
        }
        String source = request.getSource() == null || request.getSource().isBlank()
                ? "mock"
                : normalizeDatasetFolder(request.getSource());
        String fileName = normalizeDatasetFileName(request.getFileName(), "bgem3-training-data.jsonl");
        Path target = trainingDataRoot().resolve(source).resolve(fileName).normalize();
        ensureInsideTrainingData(target);
        writeAndValidateDataset(target, request.getContent());
        ModelTrainingDataset dataset = toDataset(target);
        saveDatasetRecord(dataset, request.getDatasetName(), source);
        return dataset;
    }

    public List<ModelTrainingJob> listJobs() {
        String prefix = TenantContext.tenantId() + ":";
        return jobs.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(ModelTrainingJob::getCreateTime).reversed())
                .toList();
    }

    public ModelTrainingJob getJob(String id) {
        ModelTrainingJob job = jobs.get(jobKey(id));
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
        jobs.put(jobKey(jobId), job);

        TenantContext.Context context = TenantContext.get();
        trainingExecutor.submit(() -> TenantContext.runWithContext(
                context, () -> runTraining(job, normalized, logPath, metricsPath)));
        return job;
    }

    private String jobKey(String jobId) {
        return TenantContext.tenantId() + ":" + jobId;
    }

    @PreDestroy
    public void shutdown() {
        trainingExecutor.shutdownNow();
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
        normalized.setTrainData(validateTrainingDataPath(blankToDefault(source.getTrainData(), DEFAULT_TRAIN_DATA)));
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

    private String validateTrainingDataPath(String value) {
        Path path = projectRoot().resolve(value).normalize();
        ensureInsideTrainingData(path);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("trainData does not exist: " + value);
        }
        if (!path.getFileName().toString().endsWith(".jsonl")) {
            throw new IllegalArgumentException("trainData must be a JSONL file");
        }
        return projectRoot().relativize(path).toString().replace("\\", "/");
    }

    private List<ModelTrainingDataset> listDatabaseDatasets() {
        if (datasetMapper == null) {
            return List.of();
        }
        QueryWrapper<AiDataset> wrapper = new QueryWrapper<>();
        wrapper.eq("category", TRAINING_DATASET_CATEGORY)
                .eq("format", TRAINING_DATASET_FORMAT)
                .eq("status", 1)
                .orderByDesc("update_time");
        return datasetMapper.selectList(wrapper).stream()
                .map(this::toDataset)
                .toList();
    }

    private ModelTrainingDataset toDataset(Path path) {
        Path normalized = path.normalize();
        ensureInsideTrainingData(normalized);
        ModelTrainingDataset dataset = new ModelTrainingDataset();
        dataset.setName(normalized.getFileName().toString());
        dataset.setPath(projectRoot().relativize(normalized).toString().replace("\\", "/"));
        dataset.setSource(resolveDatasetSource(normalized));
        try {
            dataset.setRecords(countJsonlRecords(normalized, true));
            dataset.setSizeBytes(Files.size(normalized));
            FileTime modified = Files.getLastModifiedTime(normalized);
            dataset.setModifiedTime(LocalDateTime.ofInstant(modified.toInstant(), ZoneId.systemDefault()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read dataset metadata: " + e.getMessage(), e);
        }
        return dataset;
    }

    private ModelTrainingDataset toDataset(AiDataset entity) {
        Path path = projectRoot().resolve(entity.getFilePath()).normalize();
        ModelTrainingDataset dataset = toDataset(path);
        dataset.setId(entity.getId());
        dataset.setName(entity.getDatasetName());
        dataset.setSource(resolveDatasetSource(path));
        if (entity.getRecordCount() != null) {
            dataset.setRecords(entity.getRecordCount());
        }
        if (entity.getSize() != null) {
            dataset.setSizeBytes(entity.getSize());
        }
        if (entity.getUpdateTime() != null) {
            dataset.setModifiedTime(entity.getUpdateTime());
        }
        return dataset;
    }

    private void saveDatasetRecord(ModelTrainingDataset dataset, String requestedName, String source) {
        if (datasetMapper == null) {
            return;
        }
        AiDataset entity = new AiDataset();
        entity.setDatasetCode("MTD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        entity.setDatasetName(normalizeDatasetName(requestedName, dataset.getName()));
        entity.setDescription("BGEM3 training dataset generated from " + source);
        entity.setCategory(TRAINING_DATASET_CATEGORY);
        entity.setFormat(TRAINING_DATASET_FORMAT);
        entity.setSize(dataset.getSizeBytes());
        entity.setFilePath(dataset.getPath());
        entity.setRecordCount((int) dataset.getRecords());
        entity.setFieldSchema("[{\"fieldName\":\"query\",\"fieldType\":\"string\"},{\"fieldName\":\"pos\",\"fieldType\":\"string[]\"},{\"fieldName\":\"neg\",\"fieldType\":\"string[]\"}]");
        entity.setStatus(1);
        entity.setOwnerId(1L);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setIsDeleted(0);

        QueryWrapper<AiDataset> existing = new QueryWrapper<>();
        existing.eq("file_path", dataset.getPath())
                .eq("category", TRAINING_DATASET_CATEGORY)
                .eq("format", TRAINING_DATASET_FORMAT);
        List<AiDataset> previousRecords = datasetMapper.selectList(existing);
        AiDataset previous = previousRecords.isEmpty() ? null : previousRecords.get(0);
        if (previous == null) {
            datasetMapper.insert(entity);
            dataset.setId(entity.getId());
        } else {
            entity.setId(previous.getId());
            entity.setDatasetCode(previous.getDatasetCode());
            entity.setCreateTime(previous.getCreateTime());
            datasetMapper.updateById(entity);
            dataset.setId(previous.getId());
        }
    }

    private String normalizeDatasetName(String requestedName, String fallback) {
        String name = requestedName == null || requestedName.isBlank() ? fallback : requestedName.trim();
        if (name.endsWith(".jsonl")) {
            name = name.substring(0, name.length() - ".jsonl".length());
        }
        return name;
    }

    private AiModel resolveGenerationModel(String modelCode) {
        if (modelMapper == null) {
            throw new IllegalStateException("No model mapper is available. Please run inside the Spring application context.");
        }
        QueryWrapper<AiModel> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1)
                .and(query -> query.eq("sdk_type", "openai").or().like("provider", "OpenAI").or().like("provider", "MiniMax"));
        if (modelCode != null && !modelCode.isBlank()) {
            wrapper.eq("model_code", modelCode.trim());
        }
        wrapper.orderByDesc("update_time").last("LIMIT 1");
        AiModel model = modelMapper.selectOne(wrapper);
        if (model == null) {
            throw new IllegalStateException("没有可用的大模型，请先在模型管理中启用 OpenAI 兼容模型。");
        }
        if (model.getEndpoint() == null || model.getEndpoint().isBlank()) {
            throw new IllegalStateException("模型 " + model.getModelCode() + " 缺少 Base URL。");
        }
        if (model.getApiKey() == null || model.getApiKey().isBlank()) {
            throw new IllegalStateException("模型 " + model.getModelCode() + " 缺少 API Key。");
        }
        return model;
    }

    private String callModelForDataset(AiModel model, String topic, int count) {
        String endpoint = model.getEndpoint().endsWith("/")
                ? model.getEndpoint() + "chat/completions"
                : model.getEndpoint() + "/chat/completions";
        Map<String, Object> body = Map.of(
                "model", model.getModelCode(),
                "temperature", model.getDefaultTemperature() == null ? BigDecimal.valueOf(0.4) : model.getDefaultTemperature(),
                "max_tokens", resolveMaxTokens(model, count),
                "messages", List.of(
                        Map.of("role", "system", "content", "你是中文检索训练数据专家，负责为 BGE-M3 微调生成高质量 query/pos/neg 样本。只输出 JSON，不要 Markdown。"),
                        Map.of("role", "user", "content", buildDatasetPrompt(topic, count))
                )
        );
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(170_000);
        String response = RestClient.builder()
                .requestFactory(requestFactory)
                .build()
                .post()
                .uri(endpoint)
                .header("Authorization", "Bearer " + model.getApiKey())
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);
        try {
            String content = extractOpenAiContent(response);
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("大模型返回内容为空。");
            }
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("解析大模型响应失败: " + e.getMessage(), e);
        }
    }

    private String buildDatasetPrompt(String topic, int count) {
        return """
                请围绕主题「%s」生成 %d 条 BGE-M3 检索微调训练样本。

                每条样本必须用于训练“查询 query 与正确文档 pos 更接近，与错误文档 neg 更远离”。
                输出 JSON 格式必须严格为：
                {
                  "records": [
                    {
                      "query": "用户可能输入的检索问题",
                      "pos": ["正确答案文档片段，必须具体、可检索、包含主题知识"],
                      "neg": ["容易混淆但错误或低相关的文档片段", "另一个错误示例"]
                    }
                  ]
                }

                内容要求：
                - query 必须是自然中文问题，不要出现 question 1、positive answer 等占位文本。
                - pos 必须包含正确示例、错误示例、原因说明或规则边界，和主题强相关。
                - neg 必须是错误代码规范、反模式、无关规范或容易误导的说法。
                - 如果主题涉及“正确示例和错误示例”，pos 中要包含具体代码片段或规则描述。
                - 不要输出 Markdown 代码围栏，不要输出解释文字。
                """.formatted(topic, count);
    }

    private List<Map<String, Object>> parseModelDatasetRecords(String modelContent, int expectedCount) {
        try {
            String json = extractJsonPayload(modelContent);
            JsonNode root = objectMapper.readTree(json);
            JsonNode recordsNode = root.isArray() ? root : root.path("records");
            if (!recordsNode.isArray()) {
                throw new IllegalArgumentException("大模型输出缺少 records 数组。");
            }
            List<Map<String, Object>> records = new ArrayList<>();
            for (JsonNode node : recordsNode) {
                if (records.size() >= expectedCount) {
                    break;
                }
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("query", node.path("query").asText(""));
                record.put("pos", toStringList(node.path("pos")));
                record.put("neg", toStringList(node.path("neg")));
                validateRecordMap(record, records.size() + 1);
                records.add(record);
            }
            if (records.isEmpty()) {
                throw new IllegalArgumentException("大模型没有生成有效训练样本。");
            }
            return records;
        } catch (IOException e) {
            throw new IllegalStateException("解析大模型生成数据失败: " + e.getMessage(), e);
        }
    }

    private List<String> toStringList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isTextual()) {
            return List.of(node.asText());
        }
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private void validateRecordMap(Map<String, Object> record, int lineNumber) {
        Object query = record.get("query");
        Object pos = record.get("pos");
        Object neg = record.get("neg");
        if (!(query instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("line " + lineNumber + ": query must be a non-empty string");
        }
        if (!(pos instanceof List<?> positives) || positives.isEmpty()
                || positives.stream().anyMatch(item -> !(item instanceof String value) || value.isBlank())) {
            throw new IllegalArgumentException("line " + lineNumber + ": pos must be a non-empty string list");
        }
        if (!(neg instanceof List<?> negatives) || negatives.isEmpty()
                || negatives.stream().anyMatch(item -> !(item instanceof String value) || value.isBlank())) {
            throw new IllegalArgumentException("line " + lineNumber + ": neg must be a non-empty string list");
        }
    }

    private String toJsonl(List<Map<String, Object>> records) {
        StringBuilder content = new StringBuilder();
        for (Map<String, Object> record : records) {
            try {
                content.append(objectMapper.writeValueAsString(record)).append('\n');
            } catch (IOException e) {
                throw new IllegalStateException("序列化训练数据失败: " + e.getMessage(), e);
            }
        }
        return content.toString();
    }

    private int resolveMaxTokens(AiModel model, int count) {
        int configured = model.getMaxTokens() == null ? 4096 : model.getMaxTokens();
        int expected = Math.max(2048, count * 450);
        int providerLimit = "MiniMax".equalsIgnoreCase(model.getProvider()) ? 8192 : 200000;
        return Math.max(1, Math.min(Math.max(configured, expected), providerLimit));
    }

    private String extractOpenAiContent(String response) throws IOException {
        JsonNode root = objectMapper.readTree(response);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        return content.isMissingNode() ? "" : content.asText();
    }

    private String extractJsonPayload(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        int arrayStart = trimmed.indexOf('[');
        int arrayEnd = trimmed.lastIndexOf(']');
        if (objectStart >= 0 && objectEnd > objectStart && (arrayStart < 0 || objectStart < arrayStart)) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1);
        }
        return trimmed;
    }

    private String resolveDatasetSource(Path path) {
        Path relative = trainingDataRoot().relativize(path);
        if (relative.getNameCount() > 1) {
            return relative.getName(0).toString();
        }
        return "local";
    }

    private void writeAndValidateDataset(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            long records = countJsonlRecords(content);
            if (records == 0) {
                throw new IllegalArgumentException("Dataset must contain at least one record");
            }
            Files.writeString(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save training dataset: " + e.getMessage(), e);
        }
    }

    private long countJsonlRecords(Path path, boolean skipInvalid) throws IOException {
        long records = 0;
        List<String> lines = Files.readAllLines(path);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            if (!skipInvalid) {
                validateDatasetLine(line, i + 1);
            }
            records++;
        }
        return records;
    }

    private long countJsonlRecords(String content) throws IOException {
        long records = 0;
        String[] lines = content.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            validateDatasetLine(line, i + 1);
            records++;
        }
        return records;
    }

    private void validateDatasetLine(String line, int lineNumber) throws IOException {
        Map<String, Object> record = objectMapper.readValue(line, new TypeReference<LinkedHashMap<String, Object>>() {});
        Object query = record.get("query");
        Object pos = record.get("pos");
        Object neg = record.get("neg");
        if (!(query instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("line " + lineNumber + ": query must be a non-empty string");
        }
        if (!(pos instanceof List<?> positives) || positives.isEmpty()
                || positives.stream().anyMatch(item -> !(item instanceof String value) || value.isBlank())) {
            throw new IllegalArgumentException("line " + lineNumber + ": pos must be a non-empty string list");
        }
        if (neg != null && (!(neg instanceof List<?> negatives)
                || negatives.stream().anyMatch(item -> !(item instanceof String value) || value.isBlank()))) {
            throw new IllegalArgumentException("line " + lineNumber + ": neg must be a string list when provided");
        }
    }

    private String normalizeDatasetFileName(String value, String fallback) {
        String fileName = value == null || value.isBlank() ? fallback : value.trim();
        fileName = fileName.replace("\\", "/");
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        fileName = fileName.replaceAll("[^A-Za-z0-9._-]", "-");
        if (!fileName.endsWith(".jsonl")) {
            fileName = fileName + ".jsonl";
        }
        if (fileName.equals(".jsonl")) {
            fileName = fallback;
        }
        return fileName;
    }

    private String normalizeDatasetFolder(String value) {
        String folder = value.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-");
        return folder.isBlank() ? "mock" : folder;
    }

    private Path trainingDataRoot() {
        return projectRoot().resolve(TRAINING_ROOT).resolve(TRAINING_DATA_DIR).normalize();
    }

    private void ensureInsideTrainingData(Path path) {
        Path dataRoot = trainingDataRoot();
        if (!path.normalize().startsWith(dataRoot)) {
            throw new IllegalArgumentException("Training dataset must stay inside " + TRAINING_ROOT + "/" + TRAINING_DATA_DIR);
        }
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
        Path current = Paths.get("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve(TRAINING_ROOT))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve(TRAINING_ROOT))) {
            return parent;
        }
        return current;
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
