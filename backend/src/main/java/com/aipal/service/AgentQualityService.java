package com.aipal.service;

import com.aipal.dto.AgentQualityEvaluationRequest;
import com.aipal.dto.AgentQualitySummary;
import com.aipal.entity.AiAgent;
import com.aipal.entity.AiAgentQualityResult;
import com.aipal.entity.AiAgentQualityRun;
import com.aipal.entity.AiAgentRuntimeConfig;
import com.aipal.entity.AiDataset;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.mapper.AiAgentQualityResultMapper;
import com.aipal.mapper.AiAgentQualityRunMapper;
import com.aipal.mapper.AiAgentRuntimeConfigMapper;
import com.aipal.mapper.AiDatasetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentQualityService {

    private final AiAgentMapper agentMapper;
    private final AiDatasetMapper datasetMapper;
    private final AiAgentRuntimeConfigMapper configMapper;
    private final AiAgentQualityRunMapper runMapper;
    private final AiAgentQualityResultMapper resultMapper;
    private final AgentService agentService;
    private final AgentRuntimeConfigService runtimeConfigService;
    private final AgentQualityMetricCalculator metricCalculator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<AgentQualitySummary> getSummary() {
        List<AiAgent> agents = agentMapper.selectList(null);
        return agents.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public List<AiAgentQualityRun> getTrends(Long agentId) {
        LambdaQueryWrapper<AiAgentQualityRun> wrapper = new LambdaQueryWrapper<AiAgentQualityRun>()
                .eq(agentId != null, AiAgentQualityRun::getAgentId, agentId)
                .eq(AiAgentQualityRun::getStatus, 2)
                .orderByAsc(AiAgentQualityRun::getCreateTime)
                .last("LIMIT 100");
        return runMapper.selectList(wrapper);
    }

    public Page<AiAgentQualityRun> listRuns(int pageNum, int pageSize, Long agentId) {
        Page<AiAgentQualityRun> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiAgentQualityRun> wrapper = new LambdaQueryWrapper<AiAgentQualityRun>()
                .eq(agentId != null, AiAgentQualityRun::getAgentId, agentId)
                .orderByDesc(AiAgentQualityRun::getCreateTime);
        return runMapper.selectPage(page, wrapper);
    }

    public List<AiAgentQualityResult> listResults(Long runId) {
        return resultMapper.selectList(
                new LambdaQueryWrapper<AiAgentQualityResult>()
                        .eq(AiAgentQualityResult::getRunId, runId)
                        .orderByAsc(AiAgentQualityResult::getSampleIndex)
        );
    }

    public AiAgentQualityRun runEvaluation(AgentQualityEvaluationRequest request) {
        if (request.getAgentId() == null) {
            throw new IllegalArgumentException("agentId is required");
        }

        AiAgent agent = agentMapper.selectById(request.getAgentId());
        if (agent == null) {
            throw new IllegalArgumentException("Agent does not exist: " + request.getAgentId());
        }
        AiAgentRuntimeConfig config = runtimeConfigService.getOrDefaultByAgentId(request.getAgentId());
        if (config.getDatasetId() == null) {
            throw new IllegalArgumentException("Dataset must be configured before evaluation");
        }
        AiDataset dataset = datasetMapper.selectById(config.getDatasetId());
        if (dataset == null) {
            throw new IllegalArgumentException("Dataset does not exist: " + config.getDatasetId());
        }

        AiAgentQualityRun run = createRun(agent, config, request.getSampleCount());
        runMapper.insert(run);

        try {
            List<Map<String, Object>> samples = loadSamples(dataset);
            int limit = resolveLimit(request.getSampleCount(), samples.size());
            List<AgentQualityMetricCalculator.MatchRecord> matchRecords = new ArrayList<>();

            for (int i = 0; i < limit; i++) {
                Map<String, Object> sample = samples.get(i);
                AiAgentQualityResult result = evaluateSample(run, config, sample, i);
                resultMapper.insert(result);
                matchRecords.add(new AgentQualityMetricCalculator.MatchRecord(
                        metricCalculator.normalize(result.getExpectedOutput()),
                        metricCalculator.normalize(result.getPredictedOutput()),
                        result.getMatched() != null && result.getMatched() == 1
                ));
            }

            AgentQualityMetricCalculator.Metrics metrics = metricCalculator.calculate(matchRecords);
            run.setSampleCount(limit);
            run.setAccuracy(metrics.getAccuracy());
            run.setPrecisionScore(metrics.getPrecisionScore());
            run.setRecallScore(metrics.getRecallScore());
            run.setF1Score(metrics.getF1Score());
            run.setStatus(2);
            run.setEndTime(LocalDateTime.now());
            run.setUpdateTime(LocalDateTime.now());
            runMapper.updateById(run);
            return run;
        } catch (Exception e) {
            log.warn("Agent quality evaluation failed: {}", e.getMessage());
            run.setStatus(3);
            run.setErrorMessage(e.getMessage());
            run.setEndTime(LocalDateTime.now());
            run.setUpdateTime(LocalDateTime.now());
            runMapper.updateById(run);
            return run;
        }
    }

    private AgentQualitySummary toSummary(AiAgent agent) {
        AgentQualitySummary summary = new AgentQualitySummary();
        summary.setAgentId(agent.getId());
        summary.setAgentCode(agent.getAgentCode());
        summary.setAgentName(agent.getAgentName());

        AiAgentRuntimeConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<AiAgentRuntimeConfig>().eq(AiAgentRuntimeConfig::getAgentId, agent.getId())
        );
        if (config != null) {
            summary.setDatasetId(config.getDatasetId());
            summary.setModelId(config.getModelId());
            summary.setTopK(config.getTopK());
            summary.setTemperature(config.getTemperature());
        }

        AiAgentQualityRun latestRun = runMapper.selectOne(
                new LambdaQueryWrapper<AiAgentQualityRun>()
                        .eq(AiAgentQualityRun::getAgentId, agent.getId())
                        .orderByDesc(AiAgentQualityRun::getCreateTime)
                        .last("LIMIT 1")
        );
        if (latestRun != null) {
            summary.setAccuracy(latestRun.getAccuracy());
            summary.setPrecisionScore(latestRun.getPrecisionScore());
            summary.setRecallScore(latestRun.getRecallScore());
            summary.setF1Score(latestRun.getF1Score());
            summary.setSampleCount(latestRun.getSampleCount());
            summary.setStatus(latestRun.getStatus());
            summary.setLastRunTime(latestRun.getEndTime() != null ? latestRun.getEndTime() : latestRun.getCreateTime());
        }
        return summary;
    }

    private AiAgentQualityRun createRun(AiAgent agent, AiAgentRuntimeConfig config, Integer sampleCount) {
        LocalDateTime now = LocalDateTime.now();
        AiAgentQualityRun run = new AiAgentQualityRun();
        run.setRunCode("AQR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        run.setAgentId(agent.getId());
        run.setAgentCode(agent.getAgentCode());
        run.setDatasetId(config.getDatasetId());
        run.setModelId(config.getModelId());
        run.setTopK(config.getTopK());
        run.setTemperature(config.getTemperature());
        run.setInputField(config.getInputField());
        run.setExpectedField(config.getExpectedField());
        run.setSampleCount(sampleCount);
        run.setStatus(1);
        run.setStartTime(now);
        run.setCreateTime(now);
        run.setUpdateTime(now);
        return run;
    }

    private AiAgentQualityResult evaluateSample(AiAgentQualityRun run, AiAgentRuntimeConfig config,
                                                Map<String, Object> sample, int index) {
        String input = stringValue(sample.get(config.getInputField()));
        String expected = stringValue(sample.get(config.getExpectedField()));
        LocalDateTime start = LocalDateTime.now();

        AiAgentQualityResult result = new AiAgentQualityResult();
        result.setRunId(run.getId());
        result.setSampleIndex(index + 1);
        result.setInputText(input);
        result.setExpectedOutput(expected);
        result.setCreateTime(start);

        try {
            Map<String, Object> params = new HashMap<>();
            params.put(config.getInputField(), input);
            params.put("input", input);
            params.put("modelId", config.getModelId());
            params.put("topK", config.getTopK());
            params.put("temperature", config.getTemperature());
            Object response = agentService.callAgent(run.getAgentId(), params);
            String predicted = extractPrediction(response);
            result.setPredictedOutput(predicted);
            result.setMatched(metricCalculator.normalize(expected).equals(metricCalculator.normalize(predicted)) ? 1 : 0);
        } catch (Exception e) {
            result.setPredictedOutput("");
            result.setMatched(0);
            result.setErrorMessage(e.getMessage());
        }

        result.setDurationMs((int) Duration.between(start, LocalDateTime.now()).toMillis());
        return result;
    }

    private List<Map<String, Object>> loadSamples(AiDataset dataset) throws IOException {
        if (dataset.getFilePath() == null || dataset.getFilePath().isBlank()) {
            throw new IllegalArgumentException("Dataset file path is required for quality evaluation");
        }
        String content = Files.readString(Path.of(dataset.getFilePath()));
        String format = dataset.getFormat() == null ? "json" : dataset.getFormat().toLowerCase();
        if ("csv".equals(format)) {
            return parseCsv(content);
        }
        if ("json".equals(format)) {
            return parseJson(content);
        }
        throw new IllegalArgumentException("Quality evaluation currently supports only json/csv datasets");
    }

    private List<Map<String, Object>> parseJson(String content) throws IOException {
        JsonNode root = objectMapper.readTree(content);
        JsonNode records = root;
        if (root.isObject()) {
            if (root.has("records")) records = root.get("records");
            else if (root.has("data")) records = root.get("data");
            else if (root.has("items")) records = root.get("items");
        }
        if (!records.isArray()) {
            Map<String, Object> one = objectMapper.convertValue(root, new TypeReference<Map<String, Object>>() {});
            return List.of(one);
        }
        return objectMapper.convertValue(records, new TypeReference<List<Map<String, Object>>>() {});
    }

    private List<Map<String, Object>> parseCsv(String content) {
        List<String> lines = content.lines().filter(line -> !line.isBlank()).toList();
        if (lines.size() < 2) {
            return List.of();
        }
        String[] headers = splitCsvLine(lines.get(0));
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] values = splitCsvLine(lines.get(i));
            Map<String, Object> record = new LinkedHashMap<>();
            for (int j = 0; j < headers.length; j++) {
                record.put(headers[j], j < values.length ? values[j] : "");
            }
            records.add(record);
        }
        return records;
    }

    private String[] splitCsvLine(String line) {
        return line.split("\\s*,\\s*", -1);
    }

    private int resolveLimit(Integer requested, int total) {
        if (total == 0) {
            throw new IllegalArgumentException("Dataset is empty");
        }
        if (requested == null || requested <= 0) {
            return total;
        }
        return Math.min(requested, total);
    }

    private String extractPrediction(Object response) {
        if (response instanceof Map<?, ?> map) {
            for (String key : List.of("prediction", "predicted", "output", "result", "answer", "message")) {
                Object value = map.get(key);
                if (value != null) {
                    return value.toString();
                }
            }
        }
        return response == null ? "" : response.toString();
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
