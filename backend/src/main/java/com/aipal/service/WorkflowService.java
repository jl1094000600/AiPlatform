package com.aipal.service;

import com.aipal.dto.WorkflowRequest;
import com.aipal.entity.Workflow;
import com.aipal.mapper.WorkflowMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final WorkflowMapper workflowMapper;
    private final WorkflowDefinitionService definitionService;

    public List<Workflow> list(String triggerType, Integer status) {
        LambdaQueryWrapper<Workflow> query = new LambdaQueryWrapper<Workflow>()
                .eq(triggerType != null && !triggerType.isBlank(), Workflow::getTriggerType, triggerType)
                .eq(status != null, Workflow::getStatus, status)
                .orderByDesc(Workflow::getUpdateTime)
                .orderByDesc(Workflow::getCreateTime);
        return workflowMapper.selectList(query);
    }

    public Workflow get(Long id) {
        Workflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow not found: " + id);
        }
        return workflow;
    }

    @Transactional
    public Workflow create(WorkflowRequest request) {
        definitionService.parseAndValidate(request.getWorkflowDefinition());
        Workflow workflow = new Workflow();
        apply(workflow, request);
        workflow.setStatus(0);
        workflow.setTenantId(TenantContext.tenantId());
        Long ownerId = TenantContext.userId();
        if (ownerId == null) {
            throw new IllegalStateException("Authenticated user is required");
        }
        workflow.setOwnerId(ownerId);
        workflow.setTriggerCount(0);
        workflow.setCreateTime(LocalDateTime.now());
        workflow.setUpdateTime(LocalDateTime.now());
        workflowMapper.insert(workflow);
        return workflow;
    }

    @Transactional
    public Workflow update(Long id, WorkflowRequest request) {
        Workflow workflow = get(id);
        definitionService.parseAndValidate(request.getWorkflowDefinition());
        apply(workflow, request);
        workflow.setStatus(0);
        workflow.setUpdateTime(LocalDateTime.now());
        if (workflowMapper.updateById(workflow) == 0) {
            throw new IllegalArgumentException("Workflow not found: " + id);
        }
        return workflow;
    }

    @Transactional
    public boolean delete(Long id) {
        get(id);
        return workflowMapper.deleteById(id) > 0;
    }

    @Transactional
    public Workflow deploy(Long id) {
        Workflow workflow = get(id);
        definitionService.parseAndValidate(workflow.getWorkflowDefinition());
        workflow.setStatus(1);
        workflow.setUpdateTime(LocalDateTime.now());
        if (workflowMapper.updateById(workflow) == 0) {
            throw new IllegalArgumentException("Workflow not found: " + id);
        }
        return workflow;
    }

    private void apply(Workflow workflow, WorkflowRequest request) {
        workflow.setWorkflowCode(request.getWorkflowCode());
        workflow.setWorkflowName(request.getWorkflowName());
        workflow.setDescription(request.getDescription());
        workflow.setTriggerType(request.getTriggerType().trim().toUpperCase());
        workflow.setTriggerConfig(request.getTriggerConfig());
        workflow.setWorkflowDefinition(request.getWorkflowDefinition());
    }
}
