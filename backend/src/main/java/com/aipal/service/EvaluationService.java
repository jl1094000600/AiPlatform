package com.aipal.service;

import com.aipal.dto.BatchEvaluationRequest;
import com.aipal.dto.EvaluationRequest;
import com.aipal.dto.EvaluationResult;
import com.aipal.entity.AiDataset;
import com.aipal.entity.AiEvaluation;
import com.aipal.entity.AiEvaluationSample;
import com.aipal.mapper.AiEvaluationMapper;
import com.aipal.mapper.AiEvaluationSampleMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_FAILED = 3;
    public static final int STATUS_CANCELLED = 4;

    private static final int DEFAULT_SAMPLE_COUNT = 100;
    private static final int MAX_SAMPLE_COUNT = 10_000;
    private static final int DEFAULT_TIMEOUT_MS = 30_000;
    private static final int MAX_TIMEOUT_MS = 300_000;
    private static final int MAX_RETRIES = 5;
    private static final int DEFAULT_CONCURRENCY = 4;
    private static final int MAX_CONCURRENCY = 32;
    private static final List<String> EXPECTED_KEYS = List.of(
            "expected_output", "expectedOutput", "expected", "label", "target", "answer");

    private final AiEvaluationMapper evaluationMapper;
    private final AiEvaluationSampleMapper sampleMapper;
    private final AgentService agentService;
    private final DatasetService datasetService;
    private final CriteriaEngineService criteriaEngineService;
    private final ObjectMapper objectMapper;

    private final ExecutorService taskExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public Page<AiEvaluation> listEvaluations(int pageNum, int pageSize, String name, Long datasetId, Long agentId) {
        Page<AiEvaluation> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200));
        QueryWrapper<AiEvaluation> wrapper = new QueryWrapper<>();
        if (name != null && !name.isBlank()) wrapper.like("evaluation_name", name.trim());
        if (datasetId != null) wrapper.eq("dataset_id", datasetId);
        if (agentId != null) wrapper.eq("agent_id", agentId);
        wrapper.orderByDesc("create_time");
        return evaluationMapper.selectPage(page, wrapper);
    }

    public AiEvaluation getEvaluationById(Long id) {
        AiEvaluation evaluation = evaluationMapper.selectById(id);
        if (evaluation == null) throw new IllegalArgumentException("Evaluation not found: " + id);
        return evaluation;
    }

    public String startEvaluation(EvaluationRequest request) {
        if (request != null && request.getAgentIds() != null && request.getAgentIds().size() > 1) {
            BatchEvaluationRequest batch = new BatchEvaluationRequest();
            batch.setDatasetIds(List.of(request.getDatasetId()));
            batch.setAgentIds(request.getAgentIds());
            batch.setCriteriaCode(request.getCriteriaCode());
            batch.setSampleCount(request.getSampleCount());
            batch.setTimeout(request.getTimeout());
            batch.setRetryCount(request.getRetryCount());
            batch.setConcurrency(request.getConcurrency());
            return startBatchEvaluation(batch);
        }
        return startEvaluationInternal(request, null, null);
    }

    private String startEvaluationInternal(EvaluationRequest request, String batchCode, Long retryOfId) {
        validateRequest(request);
        AiDataset dataset = datasetService.getDatasetById(request.getDatasetId());
        int availableSamples = datasetService.loadDatasetRecords(dataset).size();
        if (availableSamples == 0) throw new IllegalArgumentException("Dataset is empty");
        int requestedSamples = request.getSampleCount() == null ? DEFAULT_SAMPLE_COUNT : request.getSampleCount();
        if (requestedSamples == 0) requestedSamples = availableSamples;
        int totalSamples = Math.min(requestedSamples, availableSamples);
        Long executorId = TenantContext.userId();
        if (executorId == null) throw new IllegalStateException("Authenticated user is required");

        AiEvaluation evaluation = new AiEvaluation();
        String evaluationCode = "EVAL_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
        evaluation.setEvaluationCode(evaluationCode);
        evaluation.setEvaluationName("Evaluation-" + evaluationCode);
        evaluation.setDatasetId(request.getDatasetId());
        evaluation.setAgentId(resolveAgentId(request));
        evaluation.setBatchCode(batchCode);
        evaluation.setCriteriaConfig(request.getCriteriaCode());
        evaluation.setStatus(STATUS_PENDING);
        evaluation.setTotalSamples(totalSamples);
        evaluation.setCompletedSamples(0);
        evaluation.setSuccessSamples(0);
        evaluation.setFailedSamples(0);
        evaluation.setCancelRequested(0);
        evaluation.setRetryOfId(retryOfId);
        evaluation.setExecutorId(executorId);
        evaluation.setCreateTime(LocalDateTime.now());
        evaluation.setUpdateTime(LocalDateTime.now());
        evaluationMapper.insert(evaluation);
        executeEvaluationAsync(evaluation.getId(), request);
        return evaluationCode;
    }

    public void executeEvaluationAsync(Long evaluationId, EvaluationRequest request) {
        TenantContext.Context context = TenantContext.get();
        taskExecutor.submit(() -> runWithTenantContext(context, () -> executeEvaluation(evaluationId, request)));
    }

    void executeEvaluation(Long evaluationId, EvaluationRequest request) {
        AiEvaluation evaluation = getEvaluationById(evaluationId);
        if (isCancellationRequested(evaluationId)) {
            finishCancelled(evaluation);
            return;
        }
        evaluation.setStatus(STATUS_RUNNING);
        evaluation.setStartTime(LocalDateTime.now());
        evaluation.setUpdateTime(LocalDateTime.now());
        evaluationMapper.updateById(evaluation);

        try {
            List<Map<String, Object>> allRecords = datasetService.loadDatasetRecords(
                    datasetService.getDatasetById(evaluation.getDatasetId()));
            List<Map<String, Object>> selected = allRecords.subList(0,
                    Math.min(evaluation.getTotalSamples(), allRecords.size()));
            int concurrency = bounded(request.getConcurrency(), DEFAULT_CONCURRENCY, 1, MAX_CONCURRENCY);
            TenantContext.Context context = TenantContext.get();
            AiEvaluation activeEvaluation = evaluation;

            try (ExecutorService sampleExecutor = Executors.newFixedThreadPool(concurrency)) {
                CompletionService<AiEvaluationSample> completion = new ExecutorCompletionService<>(sampleExecutor);
                for (int index = 0; index < selected.size(); index++) {
                    int sampleIndex = index;
                    Map<String, Object> record = new LinkedHashMap<>(selected.get(index));
                    completion.submit(() -> callWithTenantContext(context,
                            () -> evaluateSample(activeEvaluation, sampleIndex, record, request)));
                }
                for (int i = 0; i < selected.size(); i++) {
                    if (isCancellationRequested(evaluationId)) break;
                    AiEvaluationSample sample = completion.take().get();
                    sampleMapper.insert(sample);
                    updateProgress(evaluationId, sample.getStatus() == 1);
                }
                if (isCancellationRequested(evaluationId)) sampleExecutor.shutdownNow();
            }

            evaluation = getEvaluationById(evaluationId);
            if (isCancellationRequested(evaluationId)) {
                finishCancelled(evaluation);
                return;
            }
            List<AiEvaluationSample> details = getEvaluationDetails(evaluationId);
            if (details.isEmpty() || evaluation.getSuccessSamples() == 0) {
                finishFailed(evaluation, "All samples failed");
                return;
            }
            EvaluationResult result = criteriaEngineService.evaluateSamples(evaluation, details);
            evaluation.setTotalScore(result.getTotalScore());
            evaluation.setResultData(writeJson(result));
            evaluation.setStatus(STATUS_COMPLETED);
            evaluation.setEndTime(LocalDateTime.now());
            evaluation.setUpdateTime(LocalDateTime.now());
            evaluationMapper.updateById(evaluation);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finishCancelled(getEvaluationById(evaluationId));
        } catch (ExecutionException e) {
            finishFailed(getEvaluationById(evaluationId), rootMessage(e));
        } catch (Exception e) {
            log.error("Evaluation {} failed", evaluationId, e);
            finishFailed(getEvaluationById(evaluationId), rootMessage(e));
        }
    }

    private AiEvaluationSample evaluateSample(AiEvaluation evaluation, int sampleIndex,
                                              Map<String, Object> record, EvaluationRequest request) {
        AiEvaluationSample detail = new AiEvaluationSample();
        detail.setEvaluationId(evaluation.getId());
        detail.setSampleIndex(sampleIndex);
        detail.setInputData(writeJson(record));
        detail.setExpectedOutput(extractExpected(record));
        detail.setCreateTime(LocalDateTime.now());
        detail.setUpdateTime(LocalDateTime.now());
        LocalDateTime started = LocalDateTime.now();
        int retries = bounded(request.getRetryCount(), 0, 0, MAX_RETRIES);
        int timeout = bounded(request.getTimeout(), DEFAULT_TIMEOUT_MS, 1, MAX_TIMEOUT_MS);

        Exception lastError = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            Future<Map<String, Object>> call = null;
            try {
                if (isCancellationRequested(evaluation.getId())) throw new CancellationException("Evaluation cancelled");
                Map<String, Object> params = buildSampleParams(record, request.getParams());
                TenantContext.Context context = TenantContext.get();
                call = taskExecutor.submit(() -> callWithTenantContext(context,
                        () -> agentService.callAgent(evaluation.getAgentId(), params)));
                Map<String, Object> response = call.get(timeout, TimeUnit.MILLISECONDS);
                String actual = extractActual(response);
                detail.setActualOutput(actual);
                detail.setStatus(1);
                Map<String, Double> metrics = calculateMetrics(detail.getExpectedOutput(), actual);
                detail.setScore(round((metrics.get("exact_match") + metrics.get("precision")
                        + metrics.get("recall") + metrics.get("f1")) * 25));
                detail.setMetricsData(writeJson(metrics));
                lastError = null;
                break;
            } catch (TimeoutException e) {
                if (call != null) call.cancel(true);
                lastError = new IllegalStateException("Agent call timed out after " + timeout + " ms", e);
            } catch (CancellationException e) {
                if (call != null) call.cancel(true);
                lastError = e;
                break;
            } catch (Exception e) {
                if (call != null && !call.isDone()) call.cancel(true);
                lastError = e instanceof ExecutionException executionException
                        ? new IllegalStateException(rootMessage(executionException), executionException) : e;
            }
        }

        detail.setDurationMs(Duration.between(started, LocalDateTime.now()).toMillis());
        if (lastError != null) {
            detail.setStatus(2);
            detail.setScore(0.0);
            detail.setActualOutput(detail.getActualOutput() == null ? "" : detail.getActualOutput());
            detail.setErrorMessage(rootMessage(lastError));
            Map<String, Double> metrics = calculateMetrics(detail.getExpectedOutput(), "");
            metrics.put("response_time_ms", (double) detail.getDurationMs());
            detail.setMetricsData(writeJson(metrics));
        } else {
            Map<String, Object> metrics = readJsonMap(detail.getMetricsData());
            metrics.put("response_time_ms", detail.getDurationMs().doubleValue());
            detail.setMetricsData(writeJson(metrics));
        }
        detail.setUpdateTime(LocalDateTime.now());
        return detail;
    }

    public String startBatchEvaluation(BatchEvaluationRequest request) {
        if (request == null || request.getDatasetIds() == null || request.getDatasetIds().isEmpty()
                || request.getAgentIds() == null || request.getAgentIds().isEmpty()) {
            throw new IllegalArgumentException("Batch evaluation requires datasetIds and agentIds");
        }
        String batchCode = "BATCH_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
        for (Long datasetId : request.getDatasetIds()) {
            for (Long agentId : request.getAgentIds()) {
                startEvaluationInternal(buildRequest(datasetId, agentId, request), batchCode, null);
            }
        }
        return batchCode;
    }

    public String retryEvaluation(Long evaluationId) {
        AiEvaluation previous = getEvaluationById(evaluationId);
        EvaluationRequest request = new EvaluationRequest();
        request.setDatasetId(previous.getDatasetId());
        request.setAgentId(previous.getAgentId());
        request.setCriteriaCode(previous.getCriteriaConfig());
        request.setSampleCount(previous.getTotalSamples());
        return startEvaluationInternal(request, previous.getBatchCode(), previous.getId());
    }

    public boolean cancelEvaluation(Long evaluationId) {
        AiEvaluation evaluation = getEvaluationById(evaluationId);
        if (evaluation.getStatus() == STATUS_COMPLETED || evaluation.getStatus() == STATUS_FAILED
                || evaluation.getStatus() == STATUS_CANCELLED) return false;
        evaluation.setCancelRequested(1);
        if (evaluation.getStatus() == STATUS_PENDING) {
            evaluation.setStatus(STATUS_CANCELLED);
            evaluation.setEndTime(LocalDateTime.now());
        }
        evaluation.setUpdateTime(LocalDateTime.now());
        return evaluationMapper.updateById(evaluation) > 0;
    }

    public String getEvaluationStatus(String evaluationCode) {
        AiEvaluation evaluation = evaluationMapper.selectOne(
                new QueryWrapper<AiEvaluation>().eq("evaluation_code", evaluationCode));
        return evaluation == null ? "unknown" : statusName(evaluation.getStatus());
    }

    public Map<String, Object> getEvaluationProgress(Long evaluationId) {
        AiEvaluation evaluation = getEvaluationById(evaluationId);
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("evaluationId", evaluation.getId());
        progress.put("evaluationCode", evaluation.getEvaluationCode());
        progress.put("status", statusName(evaluation.getStatus()));
        progress.put("totalSamples", valueOrZero(evaluation.getTotalSamples()));
        progress.put("completedSamples", valueOrZero(evaluation.getCompletedSamples()));
        progress.put("successSamples", valueOrZero(evaluation.getSuccessSamples()));
        progress.put("failedSamples", valueOrZero(evaluation.getFailedSamples()));
        int total = valueOrZero(evaluation.getTotalSamples());
        progress.put("percent", total == 0 ? 0 : Math.round(valueOrZero(evaluation.getCompletedSamples()) * 1000.0 / total) / 10.0);
        progress.put("errorMessage", evaluation.getErrorMessage());
        return progress;
    }

    public List<AiEvaluationSample> getEvaluationDetails(Long evaluationId) {
        getEvaluationById(evaluationId);
        return sampleMapper.selectList(new QueryWrapper<AiEvaluationSample>()
                .eq("evaluation_id", evaluationId).orderByAsc("sample_index"));
    }

    public Map<String, Object> getEvaluationResult(Long evaluationId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evaluation", getEvaluationById(evaluationId));
        result.put("details", getEvaluationDetails(evaluationId));
        return result;
    }

    public List<AiEvaluation> getBatchEvaluations(String batchCode) {
        return evaluationMapper.selectList(new QueryWrapper<AiEvaluation>()
                .eq("batch_code", batchCode).orderByAsc("create_time"));
    }

    public byte[] exportEvaluation(Long evaluationId, String format) {
        String normalized = format == null ? "json" : format.toLowerCase(Locale.ROOT);
        if ("json".equals(normalized)) return writeJson(getEvaluationResult(evaluationId)).getBytes(StandardCharsets.UTF_8);
        if (!"csv".equals(normalized)) throw new IllegalArgumentException("Supported export formats: json, csv");
        StringBuilder csv = new StringBuilder("sampleIndex,status,input,expected,actual,score,durationMs,error\n");
        for (AiEvaluationSample detail : getEvaluationDetails(evaluationId)) {
            csv.append(detail.getSampleIndex()).append(',')
                    .append(detail.getStatus() == 1 ? "success" : "failed").append(',')
                    .append(csv(detail.getInputData())).append(',')
                    .append(csv(detail.getExpectedOutput())).append(',')
                    .append(csv(detail.getActualOutput())).append(',')
                    .append(detail.getScore() == null ? "" : detail.getScore()).append(',')
                    .append(detail.getDurationMs() == null ? "" : detail.getDurationMs()).append(',')
                    .append(csv(detail.getErrorMessage())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private EvaluationRequest buildRequest(Long datasetId, Long agentId, BatchEvaluationRequest batchRequest) {
        EvaluationRequest request = new EvaluationRequest();
        request.setDatasetId(datasetId);
        request.setAgentId(agentId);
        request.setCriteriaCode(batchRequest.getCriteriaCode());
        request.setSampleCount(batchRequest.getSampleCount());
        request.setTimeout(batchRequest.getTimeout());
        request.setRetryCount(batchRequest.getRetryCount());
        request.setConcurrency(batchRequest.getConcurrency());
        return request;
    }

    private void validateRequest(EvaluationRequest request) {
        if (request == null || request.getDatasetId() == null) throw new IllegalArgumentException("datasetId is required");
        resolveAgentId(request);
        if (request.getSampleCount() != null && (request.getSampleCount() < 0 || request.getSampleCount() > MAX_SAMPLE_COUNT)) {
            throw new IllegalArgumentException("sampleCount must be 0 (all samples) or between 1 and 10000");
        }
        bounded(request.getTimeout(), DEFAULT_TIMEOUT_MS, 1, MAX_TIMEOUT_MS);
        bounded(request.getRetryCount(), 0, 0, MAX_RETRIES);
        bounded(request.getConcurrency(), DEFAULT_CONCURRENCY, 1, MAX_CONCURRENCY);
    }

    private Long resolveAgentId(EvaluationRequest request) {
        Long agentId = request.getAgentId();
        if (agentId == null && request.getAgentIds() != null && !request.getAgentIds().isEmpty()) {
            agentId = request.getAgentIds().get(0);
        }
        if (agentId == null) throw new IllegalArgumentException("agentId is required");
        return agentId;
    }

    private Map<String, Object> buildSampleParams(Map<String, Object> record, Map<String, Object> baseParams) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (baseParams != null) params.putAll(baseParams);
        params.putAll(record);
        params.put("sample", record);
        params.putIfAbsent("input", firstValue(record, List.of("input", "question", "prompt", "text"), record));
        return params;
    }

    private String extractExpected(Map<String, Object> record) {
        Object expected = firstValue(record, EXPECTED_KEYS, "");
        return expected == null ? "" : stringify(expected);
    }

    private Object firstValue(Map<String, Object> record, List<String> keys, Object fallback) {
        for (String key : keys) if (record.containsKey(key) && record.get(key) != null) return record.get(key);
        return fallback;
    }

    private String extractActual(Object response) {
        if (response instanceof Map<?, ?> map) {
            for (String key : List.of("prediction", "predicted", "output", "result", "answer", "message")) {
                if (map.get(key) != null) return stringify(map.get(key));
            }
        }
        return stringify(response);
    }

    private Map<String, Double> calculateMetrics(String expected, String actual) {
        String normalizedExpected = normalize(expected);
        String normalizedActual = normalize(actual);
        Set<String> expectedTokens = tokens(normalizedExpected);
        Set<String> actualTokens = tokens(normalizedActual);
        Set<String> intersection = new HashSet<>(expectedTokens);
        intersection.retainAll(actualTokens);
        double precision = actualTokens.isEmpty() ? (expectedTokens.isEmpty() ? 1 : 0)
                : (double) intersection.size() / actualTokens.size();
        double recall = expectedTokens.isEmpty() ? (actualTokens.isEmpty() ? 1 : 0)
                : (double) intersection.size() / expectedTokens.size();
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("exact_match", normalizedExpected.equals(normalizedActual) ? 1.0 : 0.0);
        metrics.put("precision", round(precision));
        metrics.put("recall", round(recall));
        metrics.put("f1", round(f1));
        return metrics;
    }

    private Set<String> tokens(String value) {
        if (value.isBlank()) return Set.of();
        return new HashSet<>(Arrays.asList(value.split("[^\\p{L}\\p{N}]+")));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private void updateProgress(Long evaluationId, boolean success) {
        AiEvaluation evaluation = getEvaluationById(evaluationId);
        evaluation.setCompletedSamples(valueOrZero(evaluation.getCompletedSamples()) + 1);
        if (success) evaluation.setSuccessSamples(valueOrZero(evaluation.getSuccessSamples()) + 1);
        else evaluation.setFailedSamples(valueOrZero(evaluation.getFailedSamples()) + 1);
        evaluation.setUpdateTime(LocalDateTime.now());
        evaluationMapper.updateById(evaluation);
    }

    private boolean isCancellationRequested(Long evaluationId) {
        AiEvaluation evaluation = evaluationMapper.selectById(evaluationId);
        return evaluation == null || evaluation.getStatus() == STATUS_CANCELLED
                || Integer.valueOf(1).equals(evaluation.getCancelRequested());
    }

    private void finishCancelled(AiEvaluation evaluation) {
        evaluation.setStatus(STATUS_CANCELLED);
        evaluation.setCancelRequested(1);
        evaluation.setEndTime(LocalDateTime.now());
        evaluation.setUpdateTime(LocalDateTime.now());
        evaluationMapper.updateById(evaluation);
    }

    private void finishFailed(AiEvaluation evaluation, String message) {
        evaluation.setStatus(STATUS_FAILED);
        evaluation.setErrorMessage(message);
        evaluation.setEndTime(LocalDateTime.now());
        evaluation.setUpdateTime(LocalDateTime.now());
        evaluationMapper.updateById(evaluation);
    }

    private String statusName(Integer status) {
        if (status == null) return "unknown";
        return switch (status) {
            case STATUS_PENDING -> "pending";
            case STATUS_RUNNING -> "running";
            case STATUS_COMPLETED -> "completed";
            case STATUS_FAILED -> "failed";
            case STATUS_CANCELLED -> "cancelled";
            default -> "unknown";
        };
    }

    private int bounded(Integer value, int defaultValue, int min, int max) {
        int resolved = value == null ? defaultValue : value;
        if (resolved < min || resolved > max) throw new IllegalArgumentException("Value must be between " + min + " and " + max);
        return resolved;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize evaluation data", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(String json) {
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse evaluation metrics", e);
        }
    }

    private String stringify(Object value) {
        if (value == null) return "";
        if (value instanceof String string) return string;
        return writeJson(value);
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private <T> T callWithTenantContext(TenantContext.Context context,
                                        java.util.concurrent.Callable<T> action) throws Exception {
        TenantContext.Context previous = TenantContext.get();
        try {
            if (context != null) TenantContext.set(context);
            return action.call();
        } finally {
            if (previous == null) TenantContext.clear();
            else TenantContext.set(previous);
        }
    }

    private void runWithTenantContext(TenantContext.Context context, Runnable action) {
        TenantContext.Context previous = TenantContext.get();
        try {
            if (context != null) TenantContext.set(context);
            action.run();
        } finally {
            if (previous == null) TenantContext.clear();
            else TenantContext.set(previous);
        }
    }

    @PreDestroy
    public void shutdown() {
        taskExecutor.shutdownNow();
    }
}
