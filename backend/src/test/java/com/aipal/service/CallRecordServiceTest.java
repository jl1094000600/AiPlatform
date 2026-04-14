package com.aipal.service;

import com.aipal.entity.MonCallRecord;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CallRecordService 集成测试
 */
@SpringBootTest
@Transactional
class CallRecordServiceTest {

    @Autowired
    private CallRecordService callRecordService;

    @Test
    void testListCallRecords() {
        Page<MonCallRecord> result = callRecordService.listCallRecords(
                1, 20, null, null, null
        );
        assertNotNull(result);
    }

    @Test
    void testCountByAgentAndTimeRange() {
        Long count = callRecordService.countByAgentAndTimeRange(
                null, null, null
        );
        assertNotNull(count);
    }

    @Test
    void testGetSuccessRateByAgentAndTimeRange() {
        Double rate = callRecordService.getSuccessRateByAgentAndTimeRange(
                null, null, null
        );
        assertNotNull(rate);
    }

    @Test
    void testGetAvgDurationByAgentAndTimeRange() {
        Double avg = callRecordService.getAvgDurationByAgentAndTimeRange(
                null, null, null
        );
        assertNotNull(avg);
    }

    @Test
    void testCountOnlineAgents() {
        Integer count = callRecordService.countOnlineAgents();
        assertNotNull(count);
        assertTrue(count >= 0);
    }

    @Test
    void testGetCurrentQps() {
        Double qps = callRecordService.getCurrentQps();
        assertNotNull(qps);
        assertTrue(qps >= 0);
    }

    @Test
    void testGetAvgResponseTime() {
        Double avg = callRecordService.getAvgResponseTime();
        assertNotNull(avg);
    }

    @Test
    void testListCallRecords_WithAgentIdFilter() {
        Page<MonCallRecord> result = callRecordService.listCallRecords(
                1, 20, 1L, null, null
        );
        assertNotNull(result);
    }

    @Test
    void testListCallRecords_WithTimeRange() {
        LocalDateTime startTime = LocalDateTime.now().minusDays(1);
        LocalDateTime endTime = LocalDateTime.now();
        Page<MonCallRecord> result = callRecordService.listCallRecords(
                1, 20, null, startTime, endTime
        );
        assertNotNull(result);
    }

    @Test
    void testListCallRecords_Pagination() {
        Page<MonCallRecord> page1 = callRecordService.listCallRecords(1, 5, null, null, null);
        assertNotNull(page1);
        Page<MonCallRecord> page2 = callRecordService.listCallRecords(2, 5, null, null, null);
        assertNotNull(page2);
    }

    @Test
    void testGetByTraceId() {
        MonCallRecord result = callRecordService.getByTraceId("non-existent-trace");
        assertTrue(result == null || result.getTraceId() != null);
    }

    @Test
    void testGetRecentRecords() {
        var records = callRecordService.getRecentRecords(10);
        assertNotNull(records);
        assertTrue(records.size() <= 10);
    }

    @Test
    void testGetTotalCallsByAgentId() {
        Long total = callRecordService.getTotalCallsByAgentId(1L);
        assertNotNull(total);
        assertTrue(total >= 0);
    }

    @Test
    void testSaveCallRecord() {
        MonCallRecord record = new MonCallRecord();
        record.setTraceId("test-trace-" + System.currentTimeMillis());
        record.setAgentId(1L);
        record.setDurationMs(100);
        record.setSuccess((byte) 1);
        record.setCreateTime(LocalDateTime.now());
        boolean saved = callRecordService.saveCallRecord(record);
        assertTrue(saved);
    }
}
