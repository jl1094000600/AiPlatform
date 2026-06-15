package com.aipal.service;

import com.aipal.entity.AiEvaluationCriteria;
import com.aipal.mapper.AiEvaluationCriteriaMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class CriteriaEngineServiceTest {
    private final CriteriaEngineService service = new CriteriaEngineService(
            mock(AiEvaluationCriteriaMapper.class), new ObjectMapper());

    @Test
    void producesDeterministicScoreFromRealMetric() {
        AiEvaluationCriteria criteria = criteria("f1", "f1");
        Map<String, Object> response = Map.of("f1", 0.875);

        assertEquals(87.5, service.calculateScore(criteria, response));
        assertEquals(87.5, service.calculateScore(criteria, response));
    }

    @Test
    void rejectsUnknownMetricInsteadOfInventingScore() {
        AiEvaluationCriteria criteria = criteria("quality", "missing_metric");

        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(criteria, Map.of("f1", 0.9)));
    }

    private AiEvaluationCriteria criteria(String code, String formula) {
        AiEvaluationCriteria criteria = new AiEvaluationCriteria();
        criteria.setCriteriaCode(code);
        criteria.setCriteriaName(code);
        criteria.setFormula(formula);
        criteria.setWeight(1.0);
        return criteria;
    }
}
