package com.aipal.service;

import com.aipal.entity.AlertEvent;
import com.aipal.entity.AlertRule;
import com.aipal.mapper.AlertEventMapper;
import com.aipal.mapper.AlertRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRuleMapper ruleMapper;
    private final AlertEventMapper eventMapper;
    private final BusinessDashboardService dashboardService;

    public Page<AlertRule> listRules(int pageNum, int pageSize) {
        Page<AlertRule> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AlertRule::getCreateTime);
        return ruleMapper.selectPage(page, wrapper);
    }

    public boolean createRule(AlertRule rule) {
        LocalDateTime now = LocalDateTime.now();
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        if (rule.getStatus() == null) rule.setStatus(1);
        if (rule.getOperator() == null) rule.setOperator(">");
        if (rule.getLevel() == null) rule.setLevel("P1");
        return ruleMapper.insert(rule) > 0;
    }

    public boolean updateRule(Long id, AlertRule rule) {
        rule.setId(id);
        rule.setUpdateTime(LocalDateTime.now());
        return ruleMapper.updateById(rule) > 0;
    }

    public boolean deleteRule(Long id) {
        return ruleMapper.deleteById(id) > 0;
    }

    public Page<AlertEvent> listEvents(int pageNum, int pageSize, String status, String level) {
        Page<AlertEvent> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) wrapper.eq(AlertEvent::getStatus, status);
        if (level != null && !level.isBlank()) wrapper.eq(AlertEvent::getLevel, level);
        wrapper.orderByDesc(AlertEvent::getTriggerTime);
        return eventMapper.selectPage(page, wrapper);
    }

    public boolean acknowledge(Long id, String ackUser) {
        AlertEvent event = new AlertEvent();
        event.setId(id);
        event.setStatus("ACKED");
        event.setAckUser(ackUser == null ? "system" : ackUser);
        event.setAckTime(LocalDateTime.now());
        return eventMapper.updateById(event) > 0;
    }

    public int evaluateRules() {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getStatus, 1);
        List<AlertRule> rules = ruleMapper.selectList(wrapper);
        Map<String, Object> summary = dashboardService.getSummary();
        int created = 0;
        for (AlertRule rule : rules) {
            BigDecimal value = metricValue(rule.getMetricType(), summary);
            if (matches(rule, value)) {
                AlertEvent event = new AlertEvent();
                event.setRuleId(rule.getId());
                event.setRuleName(rule.getRuleName());
                event.setMetricType(rule.getMetricType());
                event.setMetricValue(value);
                event.setThresholdValue(rule.getThresholdValue());
                event.setLevel(rule.getLevel());
                event.setStatus("OPEN");
                event.setMessage(rule.getRuleName() + " triggered: " + value);
                event.setTriggerTime(LocalDateTime.now());
                event.setCreateTime(LocalDateTime.now());
                eventMapper.insert(event);
                created++;
            }
        }
        return created;
    }

    private BigDecimal metricValue(String metricType, Map<String, Object> summary) {
        Object value = switch (metricType == null ? "" : metricType) {
            case "error_rate" -> summary.get("errorRate");
            case "response_time" -> summary.get("avgResponseTime");
            case "offline_agents" -> summary.get("onlineAgents");
            default -> summary.get("todayCalls");
        };
        return new BigDecimal(String.valueOf(value == null ? 0 : value));
    }

    private boolean matches(AlertRule rule, BigDecimal value) {
        BigDecimal threshold = rule.getThresholdValue() == null ? BigDecimal.ZERO : rule.getThresholdValue();
        String operator = rule.getOperator() == null ? ">" : rule.getOperator();
        int compare = value.compareTo(threshold);
        return switch (operator) {
            case ">=" -> compare >= 0;
            case "<" -> compare < 0;
            case "<=" -> compare <= 0;
            case "=" -> compare == 0;
            default -> compare > 0;
        };
    }
}
