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
class SalesQueryToolTest {

    @Mock
    private MarketingSalesDataMapper salesDataMapper;

    @InjectMocks
    private SalesQueryTool salesQueryTool;

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
        data2.setSalesDate(LocalDate.of(2026, 3, 15));
        data2.setRegion("华东");
        data2.setProductCode("P001");
        data2.setProductName("产品A");
        data2.setSalesAmount(new BigDecimal("15000.00"));
        data2.setSalesQuantity(150);
        data2.setProfitAmount(new BigDecimal("3000.00"));

        mockSalesData = List.of(data1, data2);
    }

    @Test
    void querySalesData_withDateRange_shouldReturnFilteredResults() {
        when(salesDataMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(mockSalesData);

        var request = new SalesQueryTool.SalesQueryRequest(
                "月", "华东", "P001",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );

        var result = salesQueryTool.querySalesData(request);

        assertNotNull(result);
        assertEquals("月", result.timeType());
        assertEquals("华东", result.region());
        assertEquals("P001", result.productCode());
        assertEquals(2, result.data().size());
        assertEquals(new BigDecimal("25000.00"), result.totalAmount());
        assertEquals(250, result.totalQuantity());
    }

    @Test
    void querySalesData_withNoFilters_shouldReturnAllData() {
        when(salesDataMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(mockSalesData);

        var request = new SalesQueryTool.SalesQueryRequest(
                "日", null, null,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );

        var result = salesQueryTool.querySalesData(request);

        assertNotNull(result);
        assertEquals(2, result.data().size());
    }

    @Test
    void formatPeriod_shouldFormatCorrectly() {
        assertEquals("2026-03-01", salesQueryTool.formatPeriod(LocalDate.of(2026, 3, 1), "日"));
        assertEquals("2026-W10", salesQueryTool.formatPeriod(LocalDate.of(2026, 3, 8), "周"));
        assertEquals("2026-03", salesQueryTool.formatPeriod(LocalDate.of(2026, 3, 15), "月"));
        assertEquals("2026-Q1", salesQueryTool.formatPeriod(LocalDate.of(2026, 2, 15), "季"));
        assertEquals("2026", salesQueryTool.formatPeriod(LocalDate.of(2026, 6, 15), "年"));
    }

    @Test
    void getAvailableRegions_shouldReturnRegionList() {
        var regions = salesQueryTool.getAvailableRegions();
        assertNotNull(regions);
        assertTrue(regions.contains("华东"));
        assertTrue(regions.contains("华南"));
        assertTrue(regions.contains("华北"));
    }
}
