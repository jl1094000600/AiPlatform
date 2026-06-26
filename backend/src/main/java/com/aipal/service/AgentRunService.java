package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.AgentStepType;
import com.aipal.agent.runtime.AgentTaskStatus;
import com.aipal.common.BizException;
import com.aipal.common.TraceContext;
import com.aipal.entity.AgentMemorySnapshot;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentRunEvent;
import com.aipal.entity.AgentStep;
import com.aipal.entity.AgentTask;
import com.aipal.entity.AgentArtifact;
import com.aipal.entity.AiAgent;
import com.aipal.entity.AiAgentRuntimeConfig;
import com.aipal.entity.AiAgentVersion;
import com.aipal.entity.AiMemoryProject;
import com.aipal.mapper.AgentMemorySnapshotMapper;
import com.aipal.mapper.AgentArtifactMapper;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentRunEventMapper;
import com.aipal.mapper.AgentStepMapper;
import com.aipal.mapper.AgentTaskMapper;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.security.TenantContext;
import com.aipal.service.memory.MemoryContext;
import com.aipal.service.memory.MemoryOrchestrator;
import com.aipal.service.memory.MemoryProjectService;
import com.aipal.service.memory.MemoryRecallRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentRunService {

    private final AgentRunMapper runMapper;
    private final AgentStepMapper stepMapper;
    private final AgentTaskMapper taskMapper;
    private final AgentTaskService agentTaskService;
    private final AgentMemorySnapshotMapper snapshotMapper;
    private final AgentArtifactMapper artifactMapper;
    private final AgentRunEventMapper eventMapper;
    private final AgentRunEventService eventService;
    private final AiAgentMapper agentMapper;
    private final AgentVersionService agentVersionService;
    private final AgentRuntimeConfigService runtimeConfigService;
    private final AgentRunExecutionSnapshotService executionSnapshotService;
    private final MemoryProjectService memoryProjectService;
    private final MemoryOrchestrator memoryOrchestrator;
    private final ObjectMapper objectMapper;

    @Transactional
    public AgentRun createOrReuse(CreateRunCommand command) {
        validate(command);
        try (var ignored = memoryProjectService.openAccessibleProject(command.projectKey())) {
            AiAgent agent = agentMapper.selectById(command.agentId());
            if (agent == null || agent.getStatus() == null || agent.getStatus() != 1) {
                throw new BizException(400, "Agent不存在或未上线");
            }
            AiAgentVersion version = agentVersionService.getLatestVersion(agent.getId());
            if (version == null) throw new BizException(400, "Agent没有已发布版本");
            AiAgentRuntimeConfig runtime = runtimeConfigService.getOrDefaultByAgentId(agent.getId());
            String inputJson = canonicalJson(command.input() == null ? Map.of() : command.input());
            if (inputJson.length() > 64 * 1024) throw new BizException(400, "Agent input exceeds 64KB limit");
            String idempotencyKey = sha256(TenantContext.tenantId() + "|" + command.projectKey() + "|"
                    + command.businessType() + "|" + command.businessId() + "|" + version.getId() + "|" + inputJson);
            AgentRun existing = runMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                    .eq(AgentRun::getTenantId, TenantContext.tenantId())
                    .eq(AgentRun::getIdempotencyKey, idempotencyKey).last("LIMIT 1"));
            if (existing != null) return existing;

            Map<String, Object> definition = buildDefinitionSnapshot(agent, version, runtime);
            definition.put("memoryPolicy", "resolved-at-run-creation");
            String definitionSnapshot = json(definition);
            AgentRun run = new AgentRun();
            run.setTenantId(TenantContext.tenantId());
            run.setProjectKey(command.projectKey().trim());
            run.setOwnerUserId(TenantContext.userId());
            run.setBusinessType(command.businessType().trim());
            run.setBusinessId(command.businessId().trim());
            run.setAgentId(agent.getId());
            run.setAgentVersionId(version.getId());
            run.setDefinitionSnapshot(definitionSnapshot);
            run.setDefinitionHash(sha256(definitionSnapshot));
            run.setIdempotencyKey(idempotencyKey);
            run.setStatus(AgentRunStatus.QUEUED.name());
            run.setInputJson(inputJson);
            run.setTraceId(TraceContext.generateTraceId());
            run.setMaxSteps(8);
            run.setMaxChildTasks(5);
            run.setMaxTotalTokens(8_000);
            run.setTotalTokens(0);
            run.setVersion(1);
            run.setCreateTime(LocalDateTime.now());
            run.setUpdateTime(LocalDateTime.now());
            run.setIsDeleted(0);
            try {
                runMapper.insert(run);
            } catch (DuplicateKeyException duplicate) {
                return requireByIdempotency(idempotencyKey);
            }

            executionSnapshotService.create(run, agent, version, runtime);
            createMemorySnapshot(run, command);
            createRootStep(run);
            createRootTask(run);
            eventService.record(run, null, AgentRunStatus.QUEUED.name(), "Run created");
            return run;
        }
    }

    public AgentRun get(Long runId) {
        AgentRun run = runMapper.selectById(runId);
        assertReadable(run);
        return run;
    }

    public RunView businessView(AgentRun run) {
        return toRunView(run);
    }

    public Page<AgentRun> list(int pageNum, int pageSize, String status) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) throw new BizException(400, "Invalid page range");
        if (status != null && !status.isBlank()) {
            try { AgentRunStatus.valueOf(status); }
            catch (IllegalArgumentException ex) { throw new BizException(400, "Invalid run status"); }
        }
        LambdaQueryWrapper<AgentRun> query = new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getTenantId, TenantContext.tenantId())
                .eq(status != null && !status.isBlank(), AgentRun::getStatus, status)
                .orderByDesc(AgentRun::getCreateTime);
        if (!TenantContext.hasPermission("memory:policy") && !TenantContext.hasPermission("agent:manage")) {
            if (TenantContext.userId() == null) throw new BizException(403, "User context is required");
            List<String> projectKeys = memoryProjectService.listAccessible().stream()
                    .map(AiMemoryProject::getProjectKey).toList();
            query.and(access -> {
                access.eq(AgentRun::getOwnerUserId, TenantContext.userId());
                if (!projectKeys.isEmpty()) access.or().in(AgentRun::getProjectKey, projectKeys);
            });
        }
        return runMapper.selectPage(new Page<>(pageNum, pageSize), query);
    }

    /**
     * Business-facing runtime detail. Raw input/output, prompts, memory body and artifact body
     * intentionally stay out of this projection; those need a separate audited operator flow.
     */
    public RunDetail getDetail(Long runId) {
        AgentRun run = get(runId);
        Long tenantId = TenantContext.tenantId();
        List<StepView> steps = stepMapper.selectList(new LambdaQueryWrapper<AgentStep>()
                        .eq(AgentStep::getTenantId, tenantId).eq(AgentStep::getRunId, runId)
                        .orderByAsc(AgentStep::getStepNo).orderByAsc(AgentStep::getId))
                .stream().map(step -> new StepView(step.getId(), step.getStepNo(), step.getStepType(), step.getStatus(),
                        step.getToolName(), step.getInputTokens(), step.getOutputTokens(), safeStatusMessage(step.getStatus(), step.getErrorMessage()),
                        step.getStartTime(), step.getEndTime())).toList();
        List<TaskView> tasks = taskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                        .eq(AgentTask::getTenantId, tenantId).eq(AgentTask::getRunId, runId)
                        .orderByAsc(AgentTask::getCreateTime).orderByAsc(AgentTask::getId))
                .stream().map(task -> new TaskView(task.getId(), task.getParentTaskId(), task.getTaskType(), task.getStatus(),
                        task.getAttemptCount(), task.getMaxAttempts(), safeStatusMessage(task.getStatus(), task.getErrorMessage()), task.getCreateTime(), task.getStartTime(),
                        task.getEndTime())).toList();
        List<MemorySnapshotView> memories = snapshotMapper.selectList(new LambdaQueryWrapper<AgentMemorySnapshot>()
                        .eq(AgentMemorySnapshot::getTenantId, tenantId).eq(AgentMemorySnapshot::getRunId, runId)
                        .orderByAsc(AgentMemorySnapshot::getCreateTime))
                .stream().map(snapshot -> new MemorySnapshotView(snapshot.getMemoryCode(), snapshot.getMemoryVersion(),
                        snapshot.getSourceType(), snapshot.getScopeType(), snapshot.getTokenCount(), snapshot.getPolicyVersion(),
                        snapshot.getTraceId())).toList();
        List<AgentArtifact> artifactRecords = artifactMapper.selectList(new LambdaQueryWrapper<AgentArtifact>()
                        .eq(AgentArtifact::getTenantId, tenantId).eq(AgentArtifact::getRunId, runId)
                        .orderByAsc(AgentArtifact::getCreateTime));
        List<ArtifactView> artifacts = java.util.stream.IntStream.range(0, artifactRecords.size())
                .mapToObj(index -> {
                    AgentArtifact artifact = artifactRecords.get(index);
                    return new ArtifactView(artifact.getId(), artifact.getStepId(), artifact.getArtifactType(),
                            safeArtifactTitle(artifact.getArtifactType(), index + 1), artifact.getStatus(),
                            artifact.getCreateTime(), artifact.getUpdateTime());
                }).toList();
        List<EventView> events = eventMapper.selectList(new LambdaQueryWrapper<AgentRunEvent>()
                        .eq(AgentRunEvent::getTenantId, tenantId).eq(AgentRunEvent::getRunId, runId)
                        .orderByAsc(AgentRunEvent::getCreateTime).orderByAsc(AgentRunEvent::getId))
                .stream().map(event -> new EventView(event.getId(), event.getFromStatus(), event.getToStatus(),
                        event.getActorName(), safeEventReason(event.getReason()), event.getTraceId(), event.getCreateTime()))
                .toList();
        return new RunDetail(toRunView(run), steps, tasks, memories, artifacts, events,
                memories.isEmpty() ? "No eligible project memory was referenced for this run" : null);
    }

    @Transactional
    public AgentRun cancel(Long runId, String reason) {
        AgentRun run = get(runId);
        assertCancellable(run);
        if (AgentRunStatus.valueOf(run.getStatus()).terminal()) return run;
        String fromStatus = run.getStatus();
        run.setStatus(AgentRunStatus.CANCELLED.name());
        run.setErrorMessage(reason == null || reason.isBlank() ? "Cancelled by user" : reason.trim());
        run.setEndTime(LocalDateTime.now());
        run.setVersion(run.getVersion() + 1);
        int changed = runMapper.update(run, new LambdaUpdateWrapper<AgentRun>()
                .eq(AgentRun::getId, runId).eq(AgentRun::getTenantId, TenantContext.tenantId())
                .in(AgentRun::getStatus, AgentRunStatus.QUEUED.name(), AgentRunStatus.RUNNING.name(), AgentRunStatus.WAITING_APPROVAL.name()));
        if (changed != 1) return get(runId);
        agentTaskService.cancelTasksForRun(runId, run.getErrorMessage());
        eventService.record(run, fromStatus, AgentRunStatus.CANCELLED.name(), run.getErrorMessage());
        return run;
    }

    @Transactional
    public AgentRun approve(Long runId, String reason) {
        AgentRun run = get(runId);
        assertApprover(run);
        if (!AgentRunStatus.WAITING_APPROVAL.name().equals(run.getStatus())) throw new BizException(409, "Run is not waiting for approval");
        LocalDateTime now = LocalDateTime.now();
        AgentRun update = new AgentRun();
        update.setStatus(AgentRunStatus.SUCCEEDED.name());
        update.setEndTime(now);
        update.setVersion(run.getVersion() + 1);
        update.setUpdateTime(now);
        int changed = runMapper.update(update, new LambdaUpdateWrapper<AgentRun>()
                .eq(AgentRun::getId, runId).eq(AgentRun::getTenantId, TenantContext.tenantId())
                .eq(AgentRun::getStatus, AgentRunStatus.WAITING_APPROVAL.name()));
        if (changed != 1) return get(runId);
        AgentArtifact artifactUpdate = new AgentArtifact();
        artifactUpdate.setStatus("FINAL");
        artifactUpdate.setUpdateTime(now);
        artifactMapper.update(artifactUpdate, new LambdaUpdateWrapper<AgentArtifact>()
                .eq(AgentArtifact::getTenantId, TenantContext.tenantId())
                .eq(AgentArtifact::getRunId, runId)
                .eq(AgentArtifact::getStatus, "PENDING_APPROVAL"));
        run.setStatus(AgentRunStatus.SUCCEEDED.name());
        run.setEndTime(now);
        eventService.record(run, AgentRunStatus.WAITING_APPROVAL.name(), AgentRunStatus.SUCCEEDED.name(),
                reason == null || reason.isBlank() ? "Approved by authorized user" : reason);
        return get(runId);
    }

    @Transactional
    public AgentRun reject(Long runId, String reason) {
        AgentRun run = get(runId);
        assertApprover(run);
        if (!AgentRunStatus.WAITING_APPROVAL.name().equals(run.getStatus())) throw new BizException(409, "Run is not waiting for approval");
        LocalDateTime now = LocalDateTime.now();
        String rejectReason = reason == null || reason.isBlank() ? "Rejected by authorized user" : reason.trim();
        AgentRun update = new AgentRun();
        update.setStatus(AgentRunStatus.FAILED.name());
        update.setErrorMessage(rejectReason);
        update.setEndTime(now);
        update.setVersion(run.getVersion() + 1);
        update.setUpdateTime(now);
        int changed = runMapper.update(update, new LambdaUpdateWrapper<AgentRun>()
                .eq(AgentRun::getId, runId).eq(AgentRun::getTenantId, TenantContext.tenantId())
                .eq(AgentRun::getStatus, AgentRunStatus.WAITING_APPROVAL.name()));
        if (changed != 1) return get(runId);
        AgentArtifact artifactUpdate = new AgentArtifact();
        artifactUpdate.setStatus("REJECTED");
        artifactUpdate.setUpdateTime(now);
        artifactMapper.update(artifactUpdate, new LambdaUpdateWrapper<AgentArtifact>()
                .eq(AgentArtifact::getTenantId, TenantContext.tenantId())
                .eq(AgentArtifact::getRunId, runId)
                .eq(AgentArtifact::getStatus, "PENDING_APPROVAL"));
        run.setStatus(AgentRunStatus.FAILED.name());
        run.setErrorMessage(rejectReason);
        run.setEndTime(now);
        eventService.record(run, AgentRunStatus.WAITING_APPROVAL.name(), AgentRunStatus.FAILED.name(), rejectReason);
        return get(runId);
    }

    private void createMemorySnapshot(AgentRun run, CreateRunCommand command) {
        String summary = command.businessType() + ":" + command.businessId();
        MemoryContext memory = memoryOrchestrator.prepareContext(new MemoryRecallRequest(run.getAgentId(), null, summary));
        run.setMemoryTraceId(memory.traceId());
        runMapper.updateById(run);
        memory.selected().forEach(candidate -> {
            AgentMemorySnapshot snapshot = new AgentMemorySnapshot();
            snapshot.setTenantId(run.getTenantId());
            snapshot.setRunId(run.getId());
            snapshot.setSnapshotVersion(1);
            snapshot.setMemoryId(candidate.memory().getId());
            snapshot.setMemoryVersion(candidate.memory().getVersion());
            snapshot.setMemoryCode(candidate.memory().getMemoryCode());
            snapshot.setSourceType(candidate.memory().getSourceType());
            snapshot.setScopeType(candidate.memory().getScopeType());
            snapshot.setTokenCount(candidate.estimatedTokens());
            snapshot.setPolicyVersion(memory.policyVersion());
            snapshot.setTraceId(memory.traceId());
            snapshot.setContentSummary(truncate(candidate.memory().getContent(), 1000));
            snapshot.setCreateTime(LocalDateTime.now());
            snapshot.setIsDeleted(0);
            snapshotMapper.insert(snapshot);
        });
    }

    /**
     * Keep the Run reproducible without serializing prompt bodies, endpoint details or arbitrary
     * version configuration into a broadly readable operational table.
     */
    private Map<String, Object> buildDefinitionSnapshot(AiAgent agent, AiAgentVersion version,
                                                        AiAgentRuntimeConfig runtime) {
        Map<String, Object> definition = new LinkedHashMap<>();
        Map<String, Object> agentSnapshot = new LinkedHashMap<>();
        agentSnapshot.put("id", agent.getId());
        agentSnapshot.put("agentCode", agent.getAgentCode());
        agentSnapshot.put("modelId", agent.getModelId());
        definition.put("agent", agentSnapshot);

        Map<String, Object> versionSnapshot = new LinkedHashMap<>();
        versionSnapshot.put("id", version.getId());
        versionSnapshot.put("version", version.getVersion());
        versionSnapshot.put("configHash", sha256(defaultString(version.getConfig())));
        definition.put("agentVersion", versionSnapshot);

        Map<String, Object> runtimeSnapshot = new LinkedHashMap<>();
        runtimeSnapshot.put("modelId", runtime.getModelId());
        runtimeSnapshot.put("datasetId", runtime.getDatasetId());
        runtimeSnapshot.put("topK", runtime.getTopK());
        runtimeSnapshot.put("temperature", runtime.getTemperature());
        runtimeSnapshot.put("promptId", runtime.getPromptId());
        runtimeSnapshot.put("promptVersionId", runtime.getPromptVersionId());
        runtimeSnapshot.put("systemPromptHash", sha256(defaultString(runtime.getSystemPrompt())));
        runtimeSnapshot.put("userPromptTemplateHash", sha256(defaultString(runtime.getUserPromptTemplate())));
        definition.put("runtimeConfig", runtimeSnapshot);
        definition.put("snapshotFormat", "agent-runtime-v1");
        return definition;
    }

    private void createRootStep(AgentRun run) {
        AgentStep step = new AgentStep();
        step.setTenantId(run.getTenantId());
        step.setRunId(run.getId());
        step.setStepNo(1);
        step.setStepType(AgentStepType.SNAPSHOT.name());
        step.setStatus(AgentTaskStatus.SUCCEEDED.name());
        step.setTraceId(run.getTraceId());
        step.setOutputJson("{\"message\":\"Run and memory snapshot created\"}");
        step.setStartTime(LocalDateTime.now());
        step.setEndTime(LocalDateTime.now());
        step.setCreateTime(LocalDateTime.now());
        step.setUpdateTime(LocalDateTime.now());
        step.setIsDeleted(0);
        stepMapper.insert(step);
    }

    private void createRootTask(AgentRun run) {
        AgentTask task = new AgentTask();
        task.setTenantId(run.getTenantId());
        task.setRunId(run.getId());
        task.setTaskType("RUN");
        task.setStatus(AgentTaskStatus.QUEUED.name());
        task.setPayloadJson(run.getInputJson());
        task.setAttemptCount(0);
        task.setMaxAttempts(3);
        task.setAvailableAt(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        task.setIsDeleted(0);
        taskMapper.insert(task);
    }

    private AgentRun requireByIdempotency(String idempotencyKey) {
        AgentRun run = runMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getTenantId, TenantContext.tenantId())
                .eq(AgentRun::getIdempotencyKey, idempotencyKey).last("LIMIT 1"));
        if (run == null) throw new BizException(409, "并发创建Run冲突，请重试");
        return run;
    }

    private void validate(CreateRunCommand command) {
        if (command == null || command.agentId() == null || blank(command.projectKey())
                || blank(command.businessType()) || blank(command.businessId())) {
            throw new BizException(400, "agentId、projectKey、businessType、businessId不能为空");
        }
    }

    private void assertReadable(AgentRun run) {
        if (run == null || !TenantContext.tenantId().equals(run.getTenantId())) throw new BizException(404, "Agent Run不存在");
        if (TenantContext.hasPermission("memory:policy") || TenantContext.hasPermission("agent:manage")) return;
        if (TenantContext.userId() == null) throw new BizException(403, "User context is required");
        if (TenantContext.userId().equals(run.getOwnerUserId())) return;
        try (var ignored = memoryProjectService.openAccessibleProject(run.getProjectKey())) {
            // Project membership is verified by the trusted context service.
        }
    }

    private void assertCancellable(AgentRun run) {
        if (TenantContext.hasPermission("memory:policy") || TenantContext.hasPermission("agent:manage")) return;
        if (TenantContext.userId() == null) throw new BizException(403, "User context is required");
        if (TenantContext.userId().equals(run.getOwnerUserId()) || memoryProjectService.canManageProject(run.getProjectKey())) return;
        throw new BizException(403, "Only the run owner or project manager can cancel this run");
    }

    private void assertApprover(AgentRun run) {
        if (TenantContext.hasPermission("memory:policy") || TenantContext.hasPermission("agent:manage")) return;
        if (TenantContext.userId() == null) throw new BizException(403, "User context is required");
        if (TenantContext.userId().equals(run.getOwnerUserId()) || memoryProjectService.canManageProject(run.getProjectKey())) return;
        throw new BizException(403, "Only the run owner or project manager can approve this run");
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new BizException(500, "无法冻结Agent定义快照"); }
    }

    private String canonicalJson(Object value) {
        try {
            return objectMapper.copy()
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                    .writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(400, "Agent input cannot be serialized");
        }
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    private String truncate(String value, int max) { return value == null ? "" : value.substring(0, Math.min(value.length(), max)); }
    private String defaultString(String value) { return value == null ? "" : value; }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    private RunView toRunView(AgentRun run) {
        return new RunView(run.getId(), run.getProjectKey(), run.getBusinessType(), run.getBusinessId(), run.getAgentId(),
                run.getAgentVersionId(), run.getStatus(), run.getTraceId(), run.getMemoryTraceId(), run.getTotalTokens(),
                safeStatusMessage(run.getStatus(), run.getErrorMessage()), canCurrentUserCancel(run), canCurrentUserApprove(run),
                run.getCreateTime(), run.getStartTime(), run.getEndTime());
    }

    private boolean canCurrentUserCancel(AgentRun run) {
        if (AgentRunStatus.valueOf(run.getStatus()).terminal()) return false;
        if (TenantContext.hasPermission("memory:policy") || TenantContext.hasPermission("agent:manage")) return true;
        if (TenantContext.userId() == null) return false;
        return TenantContext.userId().equals(run.getOwnerUserId()) || memoryProjectService.canManageProject(run.getProjectKey());
    }

    private boolean canCurrentUserApprove(AgentRun run) {
        if (!AgentRunStatus.WAITING_APPROVAL.name().equals(run.getStatus())) return false;
        if (TenantContext.hasPermission("memory:policy") || TenantContext.hasPermission("agent:manage")) return true;
        if (TenantContext.userId() == null) return false;
        return TenantContext.userId().equals(run.getOwnerUserId()) || memoryProjectService.canManageProject(run.getProjectKey());
    }

    private String safeStatusMessage(String status, String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return null;
        if (AgentRunStatus.CANCELLED.name().equals(status) || AgentTaskStatus.CANCELLED.name().equals(status)) {
            return "Cancelled";
        }
        if (AgentRunStatus.TIMEOUT.name().equals(status)) return "Execution timed out";
        if (AgentRunStatus.FAILED.name().equals(status) || AgentTaskStatus.FAILED.name().equals(status)) {
            return "Execution failed; technical details are restricted";
        }
        return "Execution details are restricted";
    }

    private String safeArtifactTitle(String artifactType, int sequence) {
        String normalizedType = artifactType == null || artifactType.isBlank() ? "Artifact" : artifactType.trim();
        return normalizedType + " #" + sequence;
    }

    private String safeEventReason(String reason) {
        if (reason == null || reason.isBlank()) return null;
        if (reason.length() <= 256) return reason;
        return reason.substring(0, 256);
    }

    public record CreateRunCommand(Long agentId, String projectKey, String businessType, String businessId, Object input) {
    }
    public record RunDetail(RunView run, List<StepView> steps, List<TaskView> tasks,
                            List<MemorySnapshotView> memorySnapshots, List<ArtifactView> artifacts, List<EventView> events,
                            String memoryReferenceNotice) {}
    public record RunView(Long id, String projectKey, String businessType, String businessId, Long agentId,
                          Long agentVersionId, String status, String traceId, String memoryTraceId, Integer totalTokens,
                          String errorMessage, boolean canCancel, boolean canApprove, LocalDateTime createTime, LocalDateTime startTime,
                          LocalDateTime endTime) {}
    public record StepView(Long id, Integer stepNo, String stepType, String status, String toolName, Integer inputTokens,
                           Integer outputTokens, String errorMessage, LocalDateTime startTime, LocalDateTime endTime) {}
    public record TaskView(Long id, Long parentTaskId, String taskType, String status, Integer attemptCount,
                           Integer maxAttempts, String errorMessage, LocalDateTime createTime, LocalDateTime startTime,
                           LocalDateTime endTime) {}
    public record MemorySnapshotView(String memoryCode, Integer memoryVersion, String sourceType, String scopeType,
                                     Integer tokenCount, Integer policyVersion, String traceId) {}
    public record ArtifactView(Long id, Long stepId, String artifactType, String title, String status,
                               LocalDateTime createTime, LocalDateTime updateTime) {}
    public record EventView(Long id, String fromStatus, String toStatus, String actorName, String reason,
                            String traceId, LocalDateTime createTime) {}
}
