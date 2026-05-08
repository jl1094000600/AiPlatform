package com.aipal.service;

import com.aipal.entity.AlertRule;
import com.aipal.mapper.AlertEventMapper;
import com.aipal.mapper.AlertRuleMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertServiceTest {

    @Test
    void evaluateRulesCreatesEventWhenThresholdMatches() {
        AlertRule rule = new AlertRule();
        rule.setId(1L);
        rule.setRuleName("错误率告警");
        rule.setMetricType("error_rate");
        rule.setOperator(">");
        rule.setThresholdValue(new BigDecimal("5"));
        rule.setLevel("P1");

        AlertRuleMapper ruleMapper = mock(AlertRuleMapper.class);
        AlertEventMapper eventMapper = mock(AlertEventMapper.class);
        BusinessDashboardService dashboardService = mock(BusinessDashboardService.class);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        when(eventMapper.insert(any())).thenReturn(1);
        when(dashboardService.getSummary()).thenReturn(Map.of("errorRate", 10, "avgResponseTime", 100, "onlineAgents", 1, "todayCalls", 20));

        AlertService service = new AlertService(ruleMapper, eventMapper, dashboardService);

        assertEquals(1, service.evaluateRules());
    }
}
