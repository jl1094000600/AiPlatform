package com.aipal.service;

import com.aipal.mapper.AiEvaluationMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationStatisticsServiceTest {

    @Test
    void acceptsExplicitDateRangeAndKeepsLegacySignature() {
        AiEvaluationMapper mapper = mock(AiEvaluationMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        EvaluationStatisticsService service = new EvaluationStatisticsService(mapper);

        assertDoesNotThrow(() -> service.getEvaluationStatistics(
                1L, 2L, "month", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15)));
        assertDoesNotThrow(() -> service.getEvaluationStatistics(1L, 2L, "month"));
    }

    @Test
    void rejectsReversedExplicitDateRange() {
        EvaluationStatisticsService service = new EvaluationStatisticsService(mock(AiEvaluationMapper.class));

        assertThrows(IllegalArgumentException.class, () -> service.getEvaluationStatistics(
                null, null, null, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 1)));
    }
}
