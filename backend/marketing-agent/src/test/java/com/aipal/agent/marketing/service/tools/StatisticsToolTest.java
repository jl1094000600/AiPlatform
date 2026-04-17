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
class StatisticsToolTest {

    @Mock
    private MarketingSalesDataMapper salesDataMapper;

    @InjectMocks
    private StatisticsTool statisticsTool;

    private List<MarketingSalesData> mockSalesData;

    @BeforeEach
    void setUp() {
        MarketingSalesData data1 = new MarketingSalesData();
        data1.setSalesDate(LocalDate.of(2026, 3, 1));
        data1.setRegion("华东");
        data1.setProductCode("P001");
        data1.setProductName("产品A");
        data1.setSalesAmount(new BigDecimal("10000.00"));
        data1.setSalesQuantity(100);
        data1.setProfitAmount(new BigDecimal("2000.00"));

        MarketingSalesData data2 = new MarketingSalesData();
        data2.setSalesDate(LocalDate.of(2026, 3, 5));
        data2.setRegion("华东");
        data2.setProductCode("P001");
        data2.setProductName("产品A");
        data2.setSalesAmount(new BigDecimal("5000.00"));
        data2.setSalesQuantity(50);
        data2.setProfitAmount(new BigDecimal("1000.00"));

        MarketingSalesData data3 = new MarketingSalesData();
        data3.setSalesDate(LocalDate.of(2026, 3, 10));
        data3.setRegion("华南");
        data3.setProductCode("P002");
        data3.setProductName("产品B");
        data3.setSalesAmount(new BigDecimal("8000.00"));
        data3.setSalesQuantity(80);
        data3.setProfitAmount(new BigDecimal("1600.00"));

        mockSalesData = List.of(data1, data2, data3);
    }

    @Test
    void generateStatistics_withValidData_shouldReturnSummaryAndRankings() {
        when(salesDataMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(mockSalesData);

        var request = new StatisticsTool.StatisticsRequest(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                List.of("region", "product")
        );

        var result = statisticsTool.generateStatistics(request);

        assertNotNull(result);
        assertNotNull(result.summary());
        assertFalse(result.rankings().isEmpty());
        assertNotNull(result.chartData());
    }

    @Test
    void generateStatistics_shouldCalculateCorrectSummary() {
        when(salesDataMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(mockSalesData);

        var request = new StatisticsTool.StatisticsRequest(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                List.of("region")
        );

        var result = statisticsTool.generateStatistics(request);

        var summary = result.summary();
        assertEquals(new BigDecimal("23000.00"), summary.totalSalesAmount());
        assertEquals(230, summary.totalSalesQuantity());
        assertEquals(new BigDecimal("4600.00"), summary.totalProfit());
    }

    @Test
    void generateStatistics_shouldGenerateRankingsWithCorrectOrder() {
        when(salesDataMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(mockSalesData);

        var request = new StatisticsTool.StatisticsRequest(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                List.of("region")
        );

        var result = statisticsTool.generateStatistics(request);

        var regionRankings = result.rankings().stream()
                .filter(r -> "region".equals(r.dimension()))
                .toList();

        assertEquals(2, regionRankings.size());
        assertEquals(1, regionRankings.get(0).rank());
        assertEquals(2, regionRankings.get(1).rank());
    }

    @Test
    void generateStatistics_shouldGenerateChartData() {
        when(salesDataMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(mockSalesData);

        var request = new StatisticsTool.StatisticsRequest(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                List.of("region")
        );

        var result = statisticsTool.generateStatistics(request);

        var chartData = result.chartData();
        assertNotNull(chartData);
        assertNotNull(chartData.categories());
        assertNotNull(chartData.series());
        assertFalse(chartData.series().isEmpty());
    }

    @Test
    void exportData_withCsvFormat_shouldReturnCsvString() {
        when(salesDataMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(mockSalesData);

        var result = statisticsTool.exportData("csv",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31));

        assertNotNull(result);
        assertTrue(result.contains("日期"));
        assertTrue(result.contains("区域"));
        assertTrue(result.contains("产品编码"));
        assertTrue(result.contains("销售额"));
    }

    @Test
    void exportData_withUnsupportedFormat_shouldReturnError() {
        when(salesDataMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(mockSalesData);

        var result = statisticsTool.exportData("xml",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31));

        assertTrue(result.contains("Unsupported format"));
    }
}
