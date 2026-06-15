package com.aipal.service;

import com.aipal.dto.EvaluationRequest;
import com.aipal.dto.EvaluationResult;
import com.aipal.entity.AiDataset;
import com.aipal.entity.AiEvaluation;
import com.aipal.entity.AiEvaluationSample;
import com.aipal.mapper.AiEvaluationMapper;
import com.aipal.mapper.AiEvaluationSampleMapper;
import com.aipal.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationServiceTest {
    private EvaluationService service;

    @AfterEach
    void tearDown() {
        if (service != null) service.shutdown();
        TenantContext.clear();
    }

    @Test
    void evaluatesEachDatasetRecordAndPersistsProgress() {
        TenantContext.set(new TenantContext.Context(
                5L, "reviewer", 6L, "tenant-6", false, Set.of(), Set.of()));
        AiEvaluationMapper evaluationMapper = mock(AiEvaluationMapper.class);
        AiEvaluationSampleMapper sampleMapper = mock(AiEvaluationSampleMapper.class);
        AgentService agentService = mock(AgentService.class);
        DatasetService datasetService = mock(DatasetService.class);
        CriteriaEngineService criteriaEngine = mock(CriteriaEngineService.class);
        AtomicReference<AiEvaluation> stored = new AtomicReference<>(evaluation());
        List<AiEvaluationSample> samples = new ArrayList<>();

        when(evaluationMapper.selectById(9L)).thenAnswer(invocation -> stored.get());
        when(evaluationMapper.updateById(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        when(sampleMapper.insert(any())).thenAnswer(invocation -> {
            samples.add(invocation.getArgument(0));
            return 1;
        });
        when(sampleMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(samples));
        when(datasetService.getDatasetById(3L)).thenReturn(new AiDataset());
        when(datasetService.loadDatasetRecords(any())).thenReturn(List.of(
                Map.of("input", "one", "expected", "ONE"),
                Map.of("input", "two", "expected", "TWO")));
        when(agentService.callAgent(eq(4L), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) invocation.getArgument(1);
            return Map.of("output", params.get("input").toString().toUpperCase());
        });
        EvaluationResult result = new EvaluationResult();
        result.setTotalScore(100.0);
        when(criteriaEngine.evaluateSamples(any(), any())).thenReturn(result);

        service = new EvaluationService(
                evaluationMapper, sampleMapper, agentService, datasetService, criteriaEngine, new ObjectMapper());
        EvaluationRequest request = new EvaluationRequest();
        request.setDatasetId(3L);
        request.setAgentId(4L);
        request.setConcurrency(2);
        request.setTimeout(2_000);

        service.executeEvaluation(9L, request);

        assertEquals(EvaluationService.STATUS_COMPLETED, stored.get().getStatus());
        assertEquals(2, stored.get().getCompletedSamples());
        assertEquals(2, stored.get().getSuccessSamples());
        assertEquals(2, samples.size());
        assertTrue(samples.stream().allMatch(sample -> sample.getScore() == 100.0));
    }

    @Test
    void cancelsPendingEvaluationAndExportsRealCsvBytes() {
        AiEvaluationMapper evaluationMapper = mock(AiEvaluationMapper.class);
        AiEvaluationSampleMapper sampleMapper = mock(AiEvaluationSampleMapper.class);
        AiEvaluation pending = evaluation();
        when(evaluationMapper.selectById(9L)).thenReturn(pending);
        when(evaluationMapper.updateById(pending)).thenReturn(1);
        AiEvaluationSample sample = new AiEvaluationSample();
        sample.setSampleIndex(0);
        sample.setStatus(1);
        sample.setInputData("{\"text\":\"hello, world\"}");
        sample.setExpectedOutput("yes");
        sample.setActualOutput("yes");
        sample.setScore(100.0);
        sample.setDurationMs(12L);
        when(sampleMapper.selectList(any())).thenReturn(List.of(sample));
        service = new EvaluationService(
                evaluationMapper, sampleMapper, mock(AgentService.class), mock(DatasetService.class),
                mock(CriteriaEngineService.class), new ObjectMapper());

        assertTrue(service.cancelEvaluation(9L));
        assertEquals(EvaluationService.STATUS_CANCELLED, pending.getStatus());
        assertEquals(1, pending.getCancelRequested());
        String csv = new String(service.exportEvaluation(9L, "csv"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("sampleIndex,status,input,expected,actual,score,durationMs,error"));
        assertTrue(csv.contains("\"{\"\"text\"\":\"\"hello, world\"\"}\""));
        assertFalse(csv.contains("Result{"));
    }

    private AiEvaluation evaluation() {
        AiEvaluation evaluation = new AiEvaluation();
        evaluation.setId(9L);
        evaluation.setEvaluationCode("EVAL_TEST");
        evaluation.setDatasetId(3L);
        evaluation.setAgentId(4L);
        evaluation.setStatus(EvaluationService.STATUS_PENDING);
        evaluation.setTotalSamples(2);
        evaluation.setCompletedSamples(0);
        evaluation.setSuccessSamples(0);
        evaluation.setFailedSamples(0);
        evaluation.setCancelRequested(0);
        return evaluation;
    }
}
