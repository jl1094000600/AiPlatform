package com.aipal.service;

import com.aipal.common.ErrorCode;
import com.aipal.dto.WorkflowVersionDtos;
import com.aipal.entity.Workflow;
import com.aipal.entity.WorkflowVersion;
import com.aipal.mapper.WorkflowMapper;
import com.aipal.mapper.WorkflowVersionMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable published snapshots of workflow definitions.
 * Draft keeps mutating in ai_workflow.workflow_definition; publish freezes a validated copy.
 */
@Service
@RequiredArgsConstructor
public class WorkflowVersionService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowVersionMapper versionMapper;
    private final WorkflowDefinitionService definitionService;
    private final ObjectMapper objectMapper;

    @Transactional
    public WorkflowVersionDtos.VersionView publish(Long workflowId, String definitionJson, String layoutJson) {
        if (workflowId == null || workflowId <= 0) throw ErrorCode.WORKFLOW_ID_INVALID.ex();
        Workflow workflow = requireWorkflow(workflowId);
        definitionService.parseAndValidate(definitionJson);

        Integer maxNo = versionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                        .eq(WorkflowVersion::getTenantId, TenantContext.tenantId())
                        .eq(WorkflowVersion::getWorkflowId, workflowId)
                        .orderByDesc(WorkflowVersion::getVersionNo)
                        .last("LIMIT 1"))
                .stream().findFirst().map(WorkflowVersion::getVersionNo).orElse(0);

        WorkflowVersion version = new WorkflowVersion();
        version.setTenantId(TenantContext.tenantId());
        version.setWorkflowId(workflowId);
        version.setVersionNo(maxNo + 1);
        version.setDefinitionJson(definitionJson);
        version.setLayoutJson(layoutJson);
        version.setDefinitionHash(canonicalHash(definitionJson));
        version.setStatus("ACTIVE");
        version.setPublishedBy(TenantContext.userId());
        version.setPublishTime(LocalDateTime.now());
        version.setCreateTime(LocalDateTime.now());
        version.setUpdateTime(version.getCreateTime());
        version.setIsDeleted(0);
        versionMapper.insert(version);
        return view(version, workflow);
    }

    public List<WorkflowVersionDtos.VersionView> list(Long workflowId) {
        requireWorkflow(workflowId);
        return versionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                        .eq(WorkflowVersion::getTenantId, TenantContext.tenantId())
                        .eq(WorkflowVersion::getWorkflowId, workflowId)
                        .orderByDesc(WorkflowVersion::getVersionNo))
                .stream().map(v -> view(v, null)).toList();
    }

    public WorkflowVersionDtos.VersionDetail detail(Long workflowId, Long versionId) {
        Workflow workflow = requireWorkflow(workflowId);
        WorkflowVersion version = requireVersion(workflowId, versionId);
        return new WorkflowVersionDtos.VersionDetail(
                version.getId(), version.getWorkflowId(), version.getVersionNo(),
                version.getDefinitionJson(), version.getLayoutJson(), version.getDefinitionHash(),
                version.getStatus(), version.getPublishedBy(), version.getPublishTime(),
                workflow.getWorkflowCode(), workflow.getWorkflowName());
    }

    /** Latest ACTIVE snapshot; runs must bind to this immutable definition. */
    public WorkflowVersion requireActiveVersion(Long workflowId, Long versionId) {
        requireWorkflow(workflowId);
        WorkflowVersion version = requireVersion(workflowId, versionId);
        if (!"ACTIVE".equals(version.getStatus())) {
            throw ErrorCode.WORKFLOW_VERSION_NOT_ACTIVE.ex();
        }
        return version;
    }

    public WorkflowVersion latestActive(Long workflowId) {
        requireWorkflow(workflowId);
        return versionMapper.selectOne(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getTenantId, TenantContext.tenantId())
                .eq(WorkflowVersion::getWorkflowId, workflowId)
                .eq(WorkflowVersion::getStatus, "ACTIVE")
                .orderByDesc(WorkflowVersion::getVersionNo)
                .last("LIMIT 1"));
    }

    /**
     * Overwrite an existing snapshot in place (explicit user choice "覆盖原版本").
     * Mutating a published snapshot is a deliberate convenience action; versionNo is kept
     * so in-flight runs bound to this versionId will see the new content only after
     * re-binding. Prefer {@link #publish} for normal revisioning.
     */
    @Transactional
    public WorkflowVersionDtos.VersionView overwrite(Long workflowId, Long versionId,
                                                     String definitionJson, String layoutJson) {
        Workflow workflow = requireWorkflow(workflowId);
        definitionService.parseAndValidate(definitionJson);
        WorkflowVersion version = requireVersion(workflowId, versionId);
        version.setDefinitionJson(definitionJson);
        version.setLayoutJson(layoutJson);
        version.setDefinitionHash(canonicalHash(definitionJson));
        version.setStatus("ACTIVE");
        version.setPublishedBy(TenantContext.userId());
        version.setPublishTime(LocalDateTime.now());
        version.setUpdateTime(LocalDateTime.now());
        versionMapper.updateById(version);
        return view(version, workflow);
    }

    @Transactional
    public WorkflowVersionDtos.VersionView deprecate(Long workflowId, Long versionId) {
        Workflow workflow = requireWorkflow(workflowId);
        WorkflowVersion version = requireVersion(workflowId, versionId);
        if ("DEPRECATED".equals(version.getStatus())) {
            return view(version, workflow);
        }
        version.setStatus("DEPRECATED");
        version.setUpdateTime(LocalDateTime.now());
        versionMapper.updateById(version);
        return view(version, workflow);
    }

    private Workflow requireWorkflow(Long workflowId) {
        Workflow workflow = workflowMapper.selectOne(new LambdaQueryWrapper<Workflow>()
                .eq(Workflow::getTenantId, TenantContext.tenantId())
                .eq(Workflow::getId, workflowId)
                .last("LIMIT 1"));
        if (workflow == null) {
            throw ErrorCode.WORKFLOW_NOT_FOUND.ex();
        }
        return workflow;
    }

    private WorkflowVersion requireVersion(Long workflowId, Long versionId) {
        WorkflowVersion version = versionMapper.selectOne(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getTenantId, TenantContext.tenantId())
                .eq(WorkflowVersion::getWorkflowId, workflowId)
                .eq(WorkflowVersion::getId, versionId)
                .last("LIMIT 1"));
        if (version == null) {
            throw ErrorCode.WORKFLOW_VERSION_NOT_FOUND.ex();
        }
        return version;
    }

    /**
     * Stable hash over parsed JSON. Object keys are sorted recursively so that
     * key order and whitespace never change the binding hash.
     */
    public String canonicalHash(String definitionJson) {
        try {
            Object canonical = canonicalize(objectMapper.readTree(definitionJson));
            byte[] bytes = objectMapper.writeValueAsBytes(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw ErrorCode.AGENT_INPUT_NOT_SERIALIZABLE.ex();
        }
    }

    private Object canonicalize(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) {
            Map<String, Object> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), canonicalize(entry.getValue())));
            return sorted;
        }
        if (node.isArray()) {
            List<Object> items = new ArrayList<>();
            node.forEach(item -> items.add(canonicalize(item)));
            return items;
        }
        if (node.isTextual()) return node.asText();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isNumber()) return node.numberValue();
        return node.toString();
    }

    private WorkflowVersionDtos.VersionView view(WorkflowVersion version, Workflow workflow) {
        return new WorkflowVersionDtos.VersionView(
                version.getId(), version.getWorkflowId(),
                workflow != null ? workflow.getWorkflowCode() : null,
                workflow != null ? workflow.getWorkflowName() : null,
                version.getVersionNo(), version.getDefinitionHash(), version.getStatus(),
                version.getPublishedBy(), version.getPublishTime());
    }
}
