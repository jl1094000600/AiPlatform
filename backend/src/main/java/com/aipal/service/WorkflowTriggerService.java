package com.aipal.service;

import com.aipal.entity.Workflow;
import com.aipal.mapper.WorkflowMapper;
import com.aipal.security.TenantTaskRunner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTriggerService {
    private final WorkflowMapper workflowMapper;
    private final WorkflowExecutionService executionService;
    private final TenantTaskRunner tenantTaskRunner;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${aipal.scheduling.workflow-trigger-delay-ms:60000}",
            initialDelayString = "${aipal.scheduling.initial-delay-ms:30000}")
    public void triggerScheduledWorkflows() {
        tenantTaskRunner.forEachActiveTenant("workflow-schedule", tenant -> triggerDueSchedules(LocalDateTime.now()));
    }

    void triggerDueSchedules(LocalDateTime now) {
        for (Workflow workflow : deployedByType("SCHEDULE")) {
            try {
                Map<String, Object> config = triggerConfig(workflow);
                CronExpression expression = CronExpression.parse(String.valueOf(config.getOrDefault("cron", "")));
                LocalDateTime baseline = workflow.getLastTriggerTime() != null
                        ? workflow.getLastTriggerTime() : now.minusMinutes(1);
                LocalDateTime next = expression.next(baseline);
                if (next != null && !next.isAfter(now)) {
                    executionService.triggerWorkflow(workflow.getId(), "SCHEDULE", config);
                }
            } catch (RuntimeException exception) {
                log.error("Scheduled workflow {} could not be triggered", workflow.getId(), exception);
            }
        }
    }

    public List<String> triggerEvent(String eventType, Map<String, Object> params) {
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType is required");
        List<String> executions = new ArrayList<>();
        for (Workflow workflow : deployedByType("EVENT")) {
            Map<String, Object> config = triggerConfig(workflow);
            if (eventType.equalsIgnoreCase(String.valueOf(config.getOrDefault("eventType", "")))) {
                Map<String, Object> payload = params == null ? Map.of("eventType", eventType) : params;
                executions.add(executionService.triggerWorkflow(workflow.getId(), "EVENT", payload));
            }
        }
        return executions;
    }

    private List<Workflow> deployedByType(String triggerType) {
        return workflowMapper.selectList(new LambdaQueryWrapper<Workflow>()
                .eq(Workflow::getStatus, 1)
                .eq(Workflow::getTriggerType, triggerType));
    }

    private Map<String, Object> triggerConfig(Workflow workflow) {
        if (workflow.getTriggerConfig() == null || workflow.getTriggerConfig().isBlank()) return Map.of();
        try {
            return objectMapper.readValue(workflow.getTriggerConfig(), new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid triggerConfig for workflow " + workflow.getId(), exception);
        }
    }
}
