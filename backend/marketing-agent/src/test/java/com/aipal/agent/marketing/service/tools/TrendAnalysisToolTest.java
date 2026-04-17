package com.aipal.agent.marketing.service.tools;

import com.aipal.agent.marketing.entity.MarketingSalesData;
import com.aipal.agent.marketing.mapper.MarketingSalesDataMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrendAnalysisToolTest {

    @Mock
    private MarketingSalesDataMapper salesDataMapper;

    @InjectMocks
    private TrendAnalysisTool trendAnalysisTool;

    private List<MarketingSalesData> currentPeriodData;
    private List<MarketingSalesData> previousPeriodData;

    @BeforeEach
    void setUp() {
        MarketingSalesData current1 = new MarketingSalesData();
        current1.setSalesDate(LocalDate.of(2026, 3, 1));
        current1.setSalesAmount(new BigDecimal("10000.00"));
        current1.setSalesQuantity(100);

        MarketingSalesData current2 = new MarketingSalesData();
        current2.setSalesDate(LocalDate.of(2026, 3, 15));
        current2.setSalesAmount(new BigDecimal("15000.00"));
        current2.setSalesQuantity(150);

        currentPeriodData = List.of(current1, current2);

        MarketingSalesData previous1 = new MarketingSalesData();
        previous1.setSalesDate(LocalDate.of(2026, 2, 1));
        previous1.setSalesAmount(new BigDecimal("8000.00"));
        previous1.setSalesQuantity(80);

        MarketingSalesData previous2 = new MarketingSalesData();
        previous2.setSalesDate(LocalDate.of(2026, 2, 15));
        previous2.setSalesAmount(new BigDecimal("12000.00"));
        previous2.setSalesQuantity(120);

        previousPeriodData = List.of(previous1, previous2);
    }

    @Test
    void analyzeTrend_withValidData_shouldReturnComparisons() {
        when(salesDataMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(currentPeriodData)
                .thenReturn(previousPeriodData);

        var request = new TrendAnalysisTool.TrendAnalysisRequest(
                "region",
                "月",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );

        var result = trendAnalysisTool.analyzeTrend(request);

        assertNotNull(result);
        assertEquals("region", result.dimension());
        assertEquals("月", result.compareType());
        assertFalse(result.currentPeriod().isEmpty());
        assertFalse(result.previousPeriod().isEmpty());
    }

    @Test
    void analyzeTrend_shouldCalculateGrowthRates() {
        when(salesDataMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(currentPeriodData)
                .thenReturn(previousPeriodData);

        var request = new TrendAnalysisTool.TrendAnalysisRequest(
                "region",
                "月",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );

        var result = trendAnalysisTool.analyzeTrend(request);

        assertNotNull(result.comparisons());
        if (!result.comparisons().isEmpty()) {
            var comparison = result.comparisons().get(0);
            assertNotNull(comparison.yoyGrowthRate());
            assertNotNull(comparison.momGrowthRate());
        }
    }

    @Test
    void getPreviousPeriod_withMonthlyData_shouldReturnCorrectPreviousMonth() {
        assertEquals("2026-02", trendAnalysisTool.getPreviousPeriod("2026-03"));
        assertEquals("2025-12", trendAnalysisTool.getPreviousPeriod("2026-01"));
    }

    @Test
    void getPreviousPeriod_withWeeklyData_shouldReturnCorrectPreviousWeek() {
        assertEquals("2026-W09", trendAnalysisTool.getPreviousPeriod("2026-W10"));
        assertEquals("2025-W52", trendAnalysisTool.getPreviousPeriod("2026-W01"));
    }

    @Test
    void getPreviousPeriod_withQuarterlyData_shouldReturnCorrectPreviousQuarter() {
        assertEquals("2026-Q1", trendAnalysisTool.getPreviousPeriod("2026-Q2"));
        assertEquals("2025-Q4", trendAnalysisTool.getPreviousPeriod("2026-Q1"));
    }

    @Test
    void getPreviousPeriod_withYearlyData_shouldReturnCorrectPreviousYear() {
        assertEquals("2025", trendAnalysisTool.getPreviousPeriod("2026"));
    }
}
