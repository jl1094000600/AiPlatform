package com.aipal.service;

import com.aipal.dto.BatchEvaluationRequest;
import com.aipal.dto.EvaluationRequest;
import com.aipal.dto.EvaluationResult;
import com.aipal.entity.AiEvaluation;
import com.aipal.mapper.AiEvaluationMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final AiEvaluationMapper evaluationMapper;
    private final AgentService agentService;
    private final CriteriaEngineService criteriaEngineService;
    private final EvaluationStatisticsService statisticsService;

    private static final Map<String, String> taskStatuses = new ConcurrentHashMap<>();

    public Page<AiEvaluation> listEvaluations(int pageNum, int pageSize, String name, Long datasetId, Long agentId) {
        Page<AiEvaluation> page = new Page<>(pageNum, pageSize);
        QueryWrapper<AiEvaluation> wrapper = new QueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            wrapper.like("evaluation_name", name);
        }
        if (datasetId != null) {
            wrapper.eq("dataset_id", datasetId);
        }
        if (agentId != null) {
            wrapper.eq("agent_id", agentId);
        }
        wrapper.orderByDesc("create_time");
        return evaluationMapper.selectPage(page, wrapper);
    }

    public AiEvaluation getEvaluationById(Long id) {
        return evaluationMapper.selectById(id);
    }

    public String startEvaluation(EvaluationRequest request) {
        AiEvaluation evaluation = new AiEvaluation();
        String evaluationCode = generateEvaluationCode();
        evaluation.setEvaluationCode(evaluationCode);
        evaluation.setEvaluationName("Evaluation-" + evaluationCode);
        evaluation.setDatasetId(request.getDatasetId());

        Long agentId = request.getAgentId();
        if (agentId == null && request.getAgentIds() != null && !request.getAgentIds().isEmpty()) {
            agentId = request.getAgentIds().get(0);
        }
        evaluation.setAgentId(agentId);
        evaluation.setStatus(1);
        evaluation.setStartTime(LocalDateTime.now());
        evaluation.setExecutorId(1L);
        evaluation.setCreateTime(LocalDateTime.now());
        evaluation.setUpdateTime(LocalDateTime.now());

        if (request.getCriteriaCode() != null) {
            evaluation.setCriteriaConfig(request.getCriteriaCode());
        }

        evaluationMapper.insert(evaluation);
        taskStatuses.put(evaluationCode, "running");

        executeEvaluationAsync(evaluation.getId(), request);

        return evaluationCode;
    }

    @Async
    public void executeEvaluationAsync(Long evaluationId, EvaluationRequest request) {
        int retryCount = request.getRetryCount() != null ? request.getRetryCount() : 0;
        int timeout = request.getTimeout() != null ? request.getTimeout() : 30000;

        AiEvaluation evaluation = evaluationMapper.selectById(evaluationId);
        log.info("Starting evaluation: {} with timeout={}ms, retry={}",
                evaluation.getEvaluationCode(), timeout, retryCount);

        Object agentResponse = null;
        Exception lastException = null;

        for (int attempt = 0; attempt <= retryCount; attempt++) {
            try {
                agentResponse = agentService.callAgent(evaluation.getAgentId(), request.getParams());
                lastException = null;
                break;
            } catch (Exception e) {
                lastException = e;
                log.warn("Evaluation attempt {} failed: {}", attempt + 1, e.getMessage());
                if (attempt < retryCount) {
                    try {
                        Thread.sleep(1000 * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (lastException != null) {
            log.error("Evaluation failed after {} attempts: {}", retryCount + 1, evaluationId, lastException);
            evaluation.setStatus(3);
            evaluation.setEndTime(LocalDateTime.now());
            evaluation.setUpdateTime(LocalDateTime.now());
            evaluationMapper.updateById(evaluation);
            taskStatuses.put(evaluation.getEvaluationCode(), "failed");
            return;
        }

        try {
            EvaluationResult result = criteriaEngineService.evaluate(evaluation.getId(), agentResponse);

            evaluation.setTotalScore(result.getTotalScore());
            evaluation.setResultData(convertResultToJson(result));
            evaluation.setStatus(2);
            evaluation.setEndTime(LocalDateTime.now());
            evaluation.setUpdateTime(LocalDateTime.now());
            evaluationMapper.updateById(evaluation);

            taskStatuses.put(evaluation.getEvaluationCode(), "completed");
            log.info("Evaluation completed: {}", evaluation.getEvaluationCode());
        } catch (Exception e) {
            log.error("Evaluation result processing failed: {}", evaluationId, e);
            evaluationMapper.updateById(buildFailedEvaluation(evaluationId));
            taskStatuses.put(evaluation.getEvaluationCode(), "failed");
        }
    }

    public String startBatchEvaluation(BatchEvaluationRequest request) {
        String batchCode = "BATCH_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        int total = request.getDatasetIds().size() * request.getAgentIds().size();
        log.info("Starting batch evaluation: {}, total tasks: {}", batchCode, total);

        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (Long datasetId : request.getDatasetIds()) {
            for (Long agentId : request.getAgentIds()) {
                EvaluationRequest evalRequest = buildRequest(datasetId, agentId, request);

                if (Boolean.TRUE.equals(request.getParallel())) {
                    final Long dsId = datasetId;
                    final Long agId = agentId;
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        startEvaluation(buildRequest(dsId, agId, request));
                    });
                    futures.add(CompletableFuture.completedFuture(batchCode));
                } else {
                    startEvaluation(buildRequest(datasetId, agentId, request));
                }
            }
        }

        return batchCode;
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

    public String getEvaluationStatus(String evaluationCode) {
        return taskStatuses.getOrDefault(evaluationCode, "unknown");
    }

    public List<AiEvaluation> getBatchEvaluations(String batchCode) {
        QueryWrapper<AiEvaluation> wrapper = new QueryWrapper<>();
        wrapper.likeRight("evaluation_code", batchCode);
        return evaluationMapper.selectList(wrapper);
    }

    private String generateEvaluationCode() {
        return "EVAL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private AiEvaluation buildFailedEvaluation(Long evaluationId) {
        AiEvaluation evaluation = new AiEvaluation();
        evaluation.setId(evaluationId);
        evaluation.setStatus(3);
        evaluation.setEndTime(LocalDateTime.now());
        evaluation.setUpdateTime(LocalDateTime.now());
        return evaluation;
    }

    private String convertResultToJson(EvaluationResult result) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"totalScore\":").append(result.getTotalScore()).append(",");
        json.append("\"criteriaScores\":[");
        if (result.getCriteriaScores() != null) {
            for (int i = 0; i < result.getCriteriaScores().size(); i++) {
                EvaluationResult.CriteriaScore cs = result.getCriteriaScores().get(i);
                if (i > 0) json.append(",");
                json.append("{");
                json.append("\"criteriaCode\":\"").append(cs.getCriteriaCode()).append("\",");
                json.append("\"criteriaName\":\"").append(cs.getCriteriaName()).append("\",");
                json.append("\"score\":").append(cs.getScore()).append(",");
                json.append("\"weight\":").append(cs.getWeight());
                json.append("}");
            }
        }
        json.append("]");
        json.append("}");
        return json.toString();
    }
}