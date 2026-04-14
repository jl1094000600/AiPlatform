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
}
