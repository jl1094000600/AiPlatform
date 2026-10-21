package com.aipal.service;

import cn.hutool.core.util.IdUtil;
import com.aipal.dto.A2AMessage;
import com.aipal.common.ErrorCode;
import com.aipal.dto.WorkflowDefinition;
import com.aipal.dto.WorkflowTryRunDtos;
import com.aipal.entity.WorkflowVersion;
import com.aipal.dto.WorkflowStep;
import com.aipal.entity.Workflow;
import com.aipal.entity.WorkflowExecution;
import com.aipal.entity.WorkflowNodeRun;
import com.aipal.mapper.WorkflowExecutionMapper;
import com.aipal.mapper.WorkflowMapper;
import com.aipal.mapper.WorkflowNodeRunMapper;
import com.aipal.security.TenantContext;
import com.aipal.service.memory.MemoryContext;
import com.aipal.service.memory.MemoryOrchestrator;
import com.aipal.service.memory.MemoryRecallRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "COMPLETED", "FAILED", "CANCELLED", "TIMEOUT",
            "DRY_COMPLETED", "DRY_FAILED", "DRY_CANCELLED");
    private static final String STATUS_DRY_RUN = "DRY_RUN";
    private static final String STATUS_DRY_RUNNING = "DRY_RUNNING";
    private static final String STATUS_DRY_COMPLETED = "DRY_COMPLETED";
    private static final String STATUS_DRY_FAILED = "DRY_FAILED";
    private static final String STATUS_DRY_CANCELLED = "DRY_CANCELLED";

    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowMapper workflowMapper;
    private final ObjectMapper objectMapper;
    private final A2AMessageService a2aMessageService;
    private final AgentRegistry agentRegistry;
    private final WorkflowDefinitionService definitionService;
    private final WorkflowVersionService workflowVersionService;
    private final WorkflowAgentRunAdapter workflowAgentRunAdapter;
    private final MemoryOrchestrator memoryOrchestrator;
    private final WorkflowNodeRunMapper nodeRunMapper;
    private final WorkflowVariableResolver workflowVariableResolver = new WorkflowVariableResolver();

    private final ExecutorService workflowExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "workflow-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> timeoutTasks = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public String createExecution(Long workflowId, String triggerType, String triggerSource, String startParams) {
        Workflow workflow = requireWorkflow(workflowId);
        // Bind the immutable published snapshot at creation time; later draft edits
        // must not change this run's definition.
        WorkflowVersion version = workflowVersionService.latestActive(workflowId);
        if (version == null) {
            throw ErrorCode.WORKFLOW_VERSION_NOT_FOUND.ex();
        }
        String executionId = IdUtil.fastSimpleUUID();
        WorkflowExecution execution = new WorkflowExecution();
        execution.setTenantId(TenantContext.tenantId());
        execution.setExecutionId(executionId);
        execution.setWorkflowId(workflowId);
        execution.setVersionId(version.getId());
        execution.setDefinitionHash(version.getDefinitionHash());
        execution.setWorkflowName(workflow.getWorkflowName());
        execution.setTriggerType(normalizeTriggerType(triggerType));
        execution.setTriggerSource(triggerSource);
        execution.setStatus("PENDING");
        execution.setStartParams(startParams);
        execution.setCreateTime(LocalDateTime.now());
        executionMapper.insert(execution);
        return executionId;
    }

    @Override
    public void startExecution(String executionId) {
        WorkflowExecution execution = requireExecution(executionId);
        WorkflowDefinition definition = loadBoundDefinition(execution);
        TenantContext.Context tenantContext = TenantContext.get();

        FutureTask<Void> future = new FutureTask<>(() -> {
            runWithTenantContext(tenantContext, () -> executeWorkflow(execution, definition, false, null));
            return null;
        });
        Future<?> existing = runningTasks.putIfAbsent(executionId, future);
        if (existing != null && !existing.isDone()) {
            throw new IllegalStateException("Workflow execution is already running: " + executionId);
        }
        if (existing != null) {
            runningTasks.put(executionId, future);
        }

        int timeoutSeconds = definition.getTimeoutSeconds();
        ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(
                () -> runWithTenantContext(tenantContext, () -> timeoutExecution(executionId)),
                timeoutSeconds,
                TimeUnit.SECONDS
        );
        timeoutTasks.put(executionId, timeoutFuture);
        workflowExecutor.execute(future);
    }

    @Override
    public void executeStep(String executionId, Object step) {
        WorkflowExecution execution = requireExecution(executionId);
        WorkflowStep workflowStep = objectMapper.convertValue(step, WorkflowStep.class);
        if (workflowStep.getStepType() == null) {
            throw new IllegalArgumentException("Workflow step type is required");
        }
        if (!Set.of("AGENT", "AGENT_INVOKE", "A2A_CALL").contains(workflowStep.getStepType().toUpperCase())) {
            throw new IllegalArgumentException("executeStep only supports agent invocation steps");
        }
        WorkflowDefinition.Node node = new WorkflowDefinition.Node();
        node.setId(String.valueOf(workflowStep.getStepOrder()));
        node.setType("AGENT");
        node.setTargetAgent(workflowStep.getTargetAgent());
        node.setParams(workflowStep.getParams());
        node.setTimeout(workflowStep.getTimeout());
        node.setRetryCount(workflowStep.getRetryCount());
        Map<String, Object> context = readContext(execution);
        context.put(node.getId(), invokeAgent(execution, node, context, loadBoundDefinition(execution)).payload());
        persistContext(execution, node.getId(), context);
    }

    @Override
    public WorkflowExecution getExecution(String executionId) {
        WorkflowExecution execution = getExecutionEntity(executionId);
        if (execution != null) {
            Workflow workflow = workflowMapper.selectById(execution.getWorkflowId());
            if (workflow != null) execution.setWorkflowName(workflow.getWorkflowName());
        }
        return execution;
    }

    @Override
    @Transactional
    public void cancelExecution(String executionId) {
        WorkflowExecution execution = requireExecution(executionId);
        if (TERMINAL_STATUSES.contains(execution.getStatus())) return;
        finish(execution, "CANCELLED", null, "Execution cancelled");
        Future<?> future = runningTasks.remove(executionId);
        if (future != null) future.cancel(true);
        cancelTimeout(executionId);
    }

    @Override
    public String triggerWorkflow(Long workflowId, String triggerType, Map<String, Object> params) {
        Workflow workflow = requireWorkflow(workflowId);
        if (!Integer.valueOf(1).equals(workflow.getStatus())) {
            throw new IllegalStateException("Workflow is not deployed: " + workflowId);
        }
        definitionService.parseAndValidate(workflow.getWorkflowDefinition());
        String startParams = writeJson(params == null ? Map.of() : params);
        String executionId = createExecution(workflowId, triggerType, "API", startParams);

        workflow.setLastTriggerTime(LocalDateTime.now());
        workflow.setTriggerCount(workflow.getTriggerCount() == null ? 1 : workflow.getTriggerCount() + 1);
        workflowMapper.updateById(workflow);
        startExecution(executionId);
        return executionId;
    }

    @Override
    public List<WorkflowExecution> listExecutions(Long workflowId) {
        LambdaQueryWrapper<WorkflowExecution> query = new LambdaQueryWrapper<WorkflowExecution>()
                .eq(workflowId != null, WorkflowExecution::getWorkflowId, workflowId)
                .orderByDesc(WorkflowExecution::getCreateTime)
                .orderByDesc(WorkflowExecution::getId);
        List<WorkflowExecution> executions = executionMapper.selectList(query);
        Map<Long, String> names = new HashMap<>();
        for (WorkflowExecution execution : executions) {
            String name = names.computeIfAbsent(execution.getWorkflowId(), id -> {
                Workflow workflow = workflowMapper.selectById(id);
                return workflow == null ? null : workflow.getWorkflowName();
            });
            execution.setWorkflowName(name);
        }
        return executions;
    }

    /**
     * T20 dry-run. Persists an independent execution with status DRY_* (not production
     * statuses) so node traces stay queryable and production metrics/lists stay clean.
     * definitionJson is validated in-memory only and never written to workflow tables.
     * AGENT nodes go through the same AgentRun/A2A pipeline as production, so tool and
     * side-effect approval semantics apply unchanged.
     */
    @Override
    public WorkflowTryRunDtos.TryRunResult tryRun(Long workflowId, WorkflowTryRunDtos.TryRunRequest request) {
        Workflow workflow = requireWorkflow(workflowId);
        Map<String, Object> inputs = request == null || request.inputs() == null
                ? Map.of() : request.inputs();
        String definitionJson = request == null ? null : request.definitionJson();

        WorkflowDefinition definition;
        Long versionId = null;
        String definitionHash = null;
        if (definitionJson != null && !definitionJson.isBlank()) {
            // Draft try-run: validate only, never persist the definition.
            definition = definitionService.parseAndValidate(definitionJson);
            definitionHash = workflowVersionService.canonicalHash(definitionJson);
        } else {
            WorkflowVersion version = workflowVersionService.latestActive(workflowId);
            if (version == null) {
                throw ErrorCode.WORKFLOW_TRY_RUN_NO_DEFINITION.ex();
            }
            definition = definitionService.parseAndValidate(version.getDefinitionJson());
            versionId = version.getId();
            definitionHash = version.getDefinitionHash();
        }

        String executionId = IdUtil.fastSimpleUUID();
        WorkflowExecution execution = new WorkflowExecution();
        execution.setTenantId(TenantContext.tenantId());
        execution.setExecutionId(executionId);
        execution.setWorkflowId(workflowId);
        execution.setVersionId(versionId);
        execution.setDefinitionHash(definitionHash);
        execution.setWorkflowName(workflow.getWorkflowName());
        execution.setTriggerType("DRY_RUN");
        execution.setTriggerSource("TRY_RUN");
        execution.setStatus(STATUS_DRY_RUN);
        execution.setStartParams(writeJson(inputs));
        execution.setCreateTime(LocalDateTime.now());
        executionMapper.insert(execution);

        List<WorkflowTryRunDtos.NodeResult> nodeResults = new ArrayList<>();
        long startedAt = System.currentTimeMillis();
        executeWorkflow(execution, definition, true, nodeResults);

        WorkflowExecution finished = getExecutionEntity(executionId);
        Map<String, Object> resultMap = Map.of();
        String errorMessage = null;
        String status = STATUS_DRY_FAILED;
        if (finished != null) {
            status = finished.getStatus();
            errorMessage = finished.getErrorMessage();
            if (finished.getResult() != null && !finished.getResult().isBlank()) {
                try {
                    resultMap = objectMapper.readValue(finished.getResult(), new TypeReference<>() {});
                } catch (Exception e) {
                    log.warn("Cannot parse try-run result for {}: {}", executionId, e.getMessage());
                }
            }
        }
        return new WorkflowTryRunDtos.TryRunResult(
                executionId, status, System.currentTimeMillis() - startedAt,
                nodeResults, resultMap, errorMessage);
    }

    @Override
    public List<WorkflowTryRunDtos.NodeRunView> listNodeRuns(String executionId) {
        requireExecution(executionId);
        return nodeRunMapper.selectList(new LambdaQueryWrapper<WorkflowNodeRun>()
                        .eq(WorkflowNodeRun::getExecutionId, executionId)
                        .orderByAsc(WorkflowNodeRun::getId))
                .stream()
                .map(this::toNodeRunView)
                .toList();
    }

    private WorkflowTryRunDtos.NodeRunView toNodeRunView(WorkflowNodeRun run) {
        return new WorkflowTryRunDtos.NodeRunView(
                run.getId(), run.getExecutionId(), run.getNodeId(), run.getNodeType(),
                run.getStatus(), run.getAttempt(), run.getInputDigest(), run.getOutputDigest(),
                run.getDurationMs(), run.getErrorMessage(), run.getCreateTime(), run.getUpdateTime());
    }

    private void addNodeResult(List<WorkflowTryRunDtos.NodeResult> collector,
                               WorkflowDefinition.Node node, String status,
                               Map<String, Object> outputs, long startedAt, String error, int attempt) {
        if (collector == null) return;
        collector.add(new WorkflowTryRunDtos.NodeResult(
                node.getId(), node.getType(), status, outputs,
                System.currentTimeMillis() - startedAt, error, attempt));
    }

    private WorkflowNodeRun recordNodeStart(WorkflowExecution execution, WorkflowDefinition.Node node,
                                            Map<String, Object> context) {
        WorkflowNodeRun run = new WorkflowNodeRun();
        run.setTenantId(TenantContext.tenantId());
        run.setExecutionId(execution.getExecutionId());
        run.setNodeId(node.getId());
        run.setNodeType(node.getType());
        run.setStatus("RUNNING");
        run.setAttempt(1);
        run.setInputDigest(digestSnapshot(inputSnapshot(node, context)));
        run.setCreateTime(LocalDateTime.now());
        run.setUpdateTime(run.getCreateTime());
        run.setIsDeleted(0);
        nodeRunMapper.insert(run);
        return run;
    }

    private void completeNodeRun(WorkflowNodeRun run, Object outputs, long startedAt, int attempt) {
        run.setStatus("COMPLETED");
        run.setAttempt(attempt);
        run.setOutputDigest(digestSnapshot(outputs));
        run.setDurationMs(System.currentTimeMillis() - startedAt);
        run.setUpdateTime(LocalDateTime.now());
        nodeRunMapper.updateById(run);
    }

    private void failNodeRun(WorkflowNodeRun run, Throwable error, long startedAt, int attempt) {
        run.setStatus("FAILED");
        run.setAttempt(attempt);
        run.setDurationMs(System.currentTimeMillis() - startedAt);
        String message = error == null ? null : error.getMessage();
        if (message != null && message.length() > 2000) {
            message = message.substring(0, 2000);
        }
        run.setErrorMessage(message);
        run.setUpdateTime(LocalDateTime.now());
        nodeRunMapper.updateById(run);
    }

    /**
     * Non-sensitive structural snapshot of what a node consumes. Raw values are never
     * persisted — only {@link #digestSnapshot(Object)} of this structure is stored.
     */
    private Map<String, Object> inputSnapshot(WorkflowDefinition.Node node, Map<String, Object> context) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nodeType", node.getType());
        if (node.getParams() != null) snapshot.put("params", node.getParams());
        if (node.getInputs() != null) snapshot.put("inputs", node.getInputs());
        if ("CONDITION".equals(node.getType())) {
            snapshot.put("conditionField", node.getConditionField());
            snapshot.put("operator", node.getOperator());
            snapshot.put("conditionValue", node.getConditionValue());
            snapshot.put("condition", node.getCondition());
        }
        if ("START".equals(node.getType()) && context != null) {
            // Start params are run inputs; keep only a type/shape fingerprint.
            snapshot.put("contextKeys", new java.util.TreeSet<>(context.keySet()));
        }
        return snapshot;
    }

    /** SHA-256 hex digest of a canonicalized JSON snapshot; empty input yields null. */
    private String digestSnapshot(Object value) {
        if (value == null) return null;
        try {
            Object canonical = canonicalize(objectMapper.valueToTree(value));
            byte[] bytes = objectMapper.writeValueAsBytes(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            log.warn("Failed to digest node snapshot: {}", e.getMessage());
            return null;
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

    private void executeWorkflow(WorkflowExecution execution, WorkflowDefinition definition,
                                 boolean dryRun, List<WorkflowTryRunDtos.NodeResult> collector) {
        try {
            execution.setStatus(dryRun ? STATUS_DRY_RUNNING : "RUNNING");
            execution.setStartTime(LocalDateTime.now());
            executionMapper.updateById(execution);

            Map<String, WorkflowDefinition.Node> nodes = new LinkedHashMap<>();
            Map<String, List<WorkflowDefinition.Edge>> outgoing = new HashMap<>();
            for (WorkflowDefinition.Node node : definition.getNodes()) nodes.put(node.getId(), node);
            for (WorkflowDefinition.Edge edge : definition.getEdges()) {
                outgoing.computeIfAbsent(edge.getSource(), ignored -> new ArrayList<>()).add(edge);
            }
            String startId = definition.getNodes().stream()
                    .filter(node -> "START".equals(node.getType()))
                    .findFirst()
                    .orElseThrow()
                    .getId();
            Map<String, Object> context = new ConcurrentHashMap<>(readStartParams(execution));
            executeNode(execution, startId, nodes, outgoing, context, definition, collector);
            ensureActive(execution.getExecutionId());
            finish(execution, dryRun ? STATUS_DRY_COMPLETED : "COMPLETED", writeJson(context), null);
        } catch (CancellationException e) {
            WorkflowExecution current = getExecutionEntity(execution.getExecutionId());
            if (current != null && !TERMINAL_STATUSES.contains(current.getStatus())) {
                finish(current, dryRun ? STATUS_DRY_CANCELLED : "CANCELLED", null, "Execution cancelled");
            }
        } catch (Exception e) {
            Throwable cause = unwrap(e);
            WorkflowExecution current = getExecutionEntity(execution.getExecutionId());
            if (current != null && !TERMINAL_STATUSES.contains(current.getStatus())) {
                finish(current, dryRun ? STATUS_DRY_FAILED : "FAILED", null, cause.getMessage());
            }
            log.error("Workflow execution failed: {}", execution.getExecutionId(), cause);
        } finally {
            if (!dryRun) {
                runningTasks.remove(execution.getExecutionId());
                cancelTimeout(execution.getExecutionId());
            }
        }
    }

    private void executeNode(WorkflowExecution execution,
                             String nodeId,
                             Map<String, WorkflowDefinition.Node> nodes,
                             Map<String, List<WorkflowDefinition.Edge>> outgoing,
                             Map<String, Object> context,
                             WorkflowDefinition definition,
                             List<WorkflowTryRunDtos.NodeResult> collector) {
        ensureActive(execution.getExecutionId());
        WorkflowDefinition.Node node = nodes.get(nodeId);
        if (node == null) throw new IllegalStateException("Workflow node not found: " + nodeId);
        persistContext(execution, nodeId, context);

        long startedAt = System.currentTimeMillis();
        WorkflowNodeRun nodeRun = recordNodeStart(execution, node, context);
        try {
            switch (node.getType()) {
                case "START" -> {
                    Map<String, Object> outputs = Map.of("started", true);
                    completeNodeRun(nodeRun, outputs, startedAt, 1);
                    addNodeResult(collector, node, "COMPLETED", outputs, startedAt, null, 1);
                    executeNode(execution, onlyTarget(nodeId, outgoing), nodes, outgoing, context, definition, collector);
                }
                case "AGENT" -> {
                    AgentInvokeResult invoked = invokeAgent(execution, node, context, definition);
                    Object result = invoked.payload();
                    context.put(nodeId, result);
                    // Record outputs under nodeOutputs so {{nodes.<id>.outputs.<field>}} resolves.
                    @SuppressWarnings("unchecked")
                    Map<String, Map<String, Object>> nodeOutputs =
                            (Map<String, Map<String, Object>>) context.computeIfAbsent("nodeOutputs",
                                    key -> new HashMap<String, Map<String, Object>>());
                    if (result instanceof Map<?, ?> map) {
                        Map<String, Object> typed = new HashMap<>();
                        map.forEach((k, v) -> typed.put(String.valueOf(k), v));
                        nodeOutputs.put(nodeId, typed);
                    } else if (result != null) {
                        nodeOutputs.put(nodeId, Map.of("output", result));
                    } else {
                        nodeOutputs.put(nodeId, Map.of());
                    }
                    persistContext(execution, nodeId, context);
                    Map<String, Object> outputs = nodeOutputs.get(nodeId);
                    completeNodeRun(nodeRun, outputs, startedAt, invoked.attempts());
                    addNodeResult(collector, node, "COMPLETED", outputs, startedAt, null, invoked.attempts());
                    executeNode(execution, onlyTarget(nodeId, outgoing), nodes, outgoing, context, definition, collector);
                }
                case "CONDITION" -> {
                    String target = conditionTarget(node, outgoing.getOrDefault(nodeId, List.of()), context);
                    Map<String, Object> outputs = Map.of("target", target);
                    completeNodeRun(nodeRun, outputs, startedAt, 1);
                    addNodeResult(collector, node, "COMPLETED", outputs, startedAt, null, 1);
                    executeNode(execution, target, nodes, outgoing, context, definition, collector);
                }
                case "PARALLEL" -> {
                    executeParallel(execution, nodeId, nodes, outgoing, context, definition, collector);
                    Map<String, Object> outputs = Map.of("branches", String.valueOf(context.get(nodeId)));
                    completeNodeRun(nodeRun, outputs, startedAt, 1);
                    addNodeResult(collector, node, "COMPLETED", outputs, startedAt, null, 1);
                }
                case "END" -> {
                    context.put("completedNode", nodeId);
                    Map<String, Object> outputs = Map.of("completedNode", nodeId);
                    completeNodeRun(nodeRun, outputs, startedAt, 1);
                    addNodeResult(collector, node, "COMPLETED", outputs, startedAt, null, 1);
                }
                default -> throw new IllegalStateException("Unsupported workflow node type: " + node.getType());
            }
        } catch (RuntimeException | Error e) {
            // Only stamp failure when this node itself failed; downstream failures
            // leave the already-completed node_run untouched and just propagate.
            if (!"COMPLETED".equals(nodeRun.getStatus())) {
                int attempt = nodeRun.getAttempt() == null ? 1 : nodeRun.getAttempt();
                failNodeRun(nodeRun, e, startedAt, attempt);
                addNodeResult(collector, node, "FAILED", null, startedAt, e.getMessage(), attempt);
            }
            throw e;
        }
    }

    private void executeParallel(WorkflowExecution execution,
                                 String nodeId,
                                 Map<String, WorkflowDefinition.Node> nodes,
                                 Map<String, List<WorkflowDefinition.Edge>> outgoing,
                                 Map<String, Object> context,
                                 WorkflowDefinition definition,
                                 List<WorkflowTryRunDtos.NodeResult> collector) {
        TenantContext.Context tenantContext = TenantContext.get();
        List<CompletableFuture<Map.Entry<String, Map<String, Object>>>> branches = outgoing
                .getOrDefault(nodeId, List.of()).stream()
                .map(edge -> CompletableFuture.supplyAsync(() -> {
                    Map<String, Object> branchContext = new HashMap<>(context);
                    runWithTenantContext(tenantContext,
                            () -> executeNode(execution, edge.getTarget(), nodes, outgoing, branchContext, definition, collector));
                    return Map.entry(edge.getTarget(), branchContext);
                }, workflowExecutor))
                .toList();

        Map<String, Object> branchResults = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<String, Map<String, Object>>> branch : branches) {
            Map.Entry<String, Map<String, Object>> result = branch.join();
            branchResults.put(result.getKey(), result.getValue());
        }
        context.put(nodeId, branchResults);
        persistContext(execution, nodeId, context);
    }

    private record AgentInvokeResult(Object payload, int attempts) {
    }

    private AgentInvokeResult invokeAgent(WorkflowExecution execution, WorkflowDefinition.Node node,
                                          Map<String, Object> context, WorkflowDefinition definition) {
        // Prefer the AgentRun adapter when the workflow is bound to a project:
        // it freezes resolved inputs, reuses runs by executionId:nodeId, and checks outputsSchema.
        Workflow boundWorkflow = workflowMapper.selectById(execution.getWorkflowId());
        if (boundWorkflow != null && boundWorkflow.getProjectKey() != null
                && !boundWorkflow.getProjectKey().isBlank() && node.getAgentId() != null) {
            Map<String, Map<String, Object>> nodeOutputs = extractNodeOutputs(context);
            Map<String, Object> runInputs = extractRunInputs(execution);
            Map<String, Object> outputs = workflowAgentRunAdapter.invoke(
                    executionContext(execution, boundWorkflow), node,
                    definitionVariables(definition), runInputs, nodeOutputs);
            return new AgentInvokeResult(outputs, 1);
        }
        AgentRegistry.AgentContext agent = resolveAgent(node);
        Map<String, Object> payload = new HashMap<>();
        if (node.getParams() != null) payload.putAll(node.getParams());
        // Resolve canvas-configured inputs ({{variables.*}}/{{inputs.*}}/{{nodes.*.outputs.*}})
        // and merge into the A2A payload so I/O bindings reach the agent.
        if (node.getInputs() != null && !node.getInputs().isEmpty()) {
            Map<String, Map<String, Object>> nodeOutputs = extractNodeOutputs(context);
            Map<String, Object> runInputs = extractRunInputs(execution);
            Map<String, Object> resolvedInputs = workflowVariableResolver.resolveInputs(
                    node.getInputs(), definitionVariables(definition), runInputs, nodeOutputs);
            payload.putAll(resolvedInputs);
        }
        payload.put("workflowContext", new HashMap<>(context));
        prepareMemoryContext(execution, node, agent, payload);
        A2AMessage message = A2AMessage.builder()
                .sourceAgent("workflow-engine")
                .targetAgent(agent.getAgent().getAgentCode())
                .sessionId(execution.getExecutionId())
                .action(A2AMessage.Action.invoke)
                .payload(payload)
                .build();

        int attempts = Math.max(1, (node.getRetryCount() == null ? 0 : node.getRetryCount()) + 1);
        int timeoutSeconds = node.getTimeout() == null ? 30 : node.getTimeout();
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            ensureActive(execution.getExecutionId());
            Future<A2AMessage> call = workflowExecutor.submit(() -> agent.getHandler().apply(message));
            try {
                A2AMessage response = call.get(timeoutSeconds, TimeUnit.SECONDS);
                if (response == null) throw new IllegalStateException("Agent returned no response");
                return new AgentInvokeResult(response.getPayload(), attempt);
            } catch (InterruptedException e) {
                call.cancel(true);
                Thread.currentThread().interrupt();
                throw new CancellationException("Workflow execution interrupted");
            } catch (TimeoutException e) {
                call.cancel(true);
                lastFailure = new IllegalStateException("Agent node timed out: " + node.getId());
            } catch (ExecutionException e) {
                lastFailure = new IllegalStateException("Agent node failed: " + node.getId(), unwrap(e));
            }
        }
        throw new IllegalStateException(lastFailure == null ? "Agent invocation failed" : lastFailure.getMessage(), lastFailure);
    }

    private void prepareMemoryContext(WorkflowExecution execution, WorkflowDefinition.Node node,
                                      AgentRegistry.AgentContext agent, Map<String, Object> payload) {
        try {
            String summary = "workflow=" + execution.getWorkflowName() + "; node=" + node.getId()
                    + "; agent=" + agent.getAgent().getAgentCode();
            MemoryContext context = memoryOrchestrator.prepareContext(
                    new MemoryRecallRequest(agent.getAgent().getId(), null, summary));
            payload.put("memoryTraceId", context.traceId());
            if (context.shouldInject()) {
                payload.put("memoryContext", context.promptSection());
            }
        } catch (RuntimeException exception) {
            // Memory remains auxiliary during AUDIT/CANARY rollout; a recall failure
            // must never cancel a workflow or an A2A agent invocation.
            log.warn("Memory context preparation failed for workflow {} node {}: {}",
                    execution.getExecutionId(), node.getId(), exception.getMessage());
        }
    }

    private AgentRegistry.AgentContext resolveAgent(WorkflowDefinition.Node node) {
        AgentRegistry.AgentContext agent = null;
        if (node.getTargetAgent() != null && !node.getTargetAgent().isBlank()) {
            agent = agentRegistry.getAgent(node.getTargetAgent());
        }
        if (agent == null && node.getAgentId() != null && agentRegistry.getAllAgents() != null) {
            agent = agentRegistry.getAllAgents().stream()
                    .filter(item -> item.getAgent() != null && node.getAgentId().equals(item.getAgent().getId()))
                    .findFirst()
                    .orElse(null);
        }
        if (agent == null || agent.getAgent() == null || agent.getHandler() == null) {
            throw new IllegalStateException("Target agent is not registered for node: " + node.getId());
        }
        return agent;
    }

    private String conditionTarget(WorkflowDefinition.Node node,
                                   List<WorkflowDefinition.Edge> edges,
                                   Map<String, Object> context) {
        boolean result = evaluateCondition(node, context);
        String expected = Boolean.toString(result);
        return edges.stream()
                .filter(edge -> expected.equalsIgnoreCase(edge.getCondition())
                        || expected.equalsIgnoreCase(edge.getLabel())
                        || (result && "YES".equalsIgnoreCase(edge.getCondition()))
                        || (!result && "NO".equalsIgnoreCase(edge.getCondition())))
                .map(WorkflowDefinition.Edge::getTarget)
                .findFirst()
                .orElseGet(() -> edges.get(result ? 0 : 1).getTarget());
    }

    private boolean evaluateCondition(WorkflowDefinition.Node node, Map<String, Object> context) {
        String field = node.getConditionField();
        String operator = node.getOperator();
        Object expected = node.getConditionValue();
        JsonNode condition = node.getCondition();
        if (condition != null && condition.isObject()) {
            field = condition.path("field").asText(field);
            operator = condition.path("operator").asText(operator);
            if (condition.has("value")) expected = objectMapper.convertValue(condition.get("value"), Object.class);
        }
        Object actual = readPath(context, field);
        return switch (operator.toUpperCase()) {
            case "EXISTS" -> actual != null;
            case "EQ" -> compare(actual, expected) == 0;
            case "NE" -> compare(actual, expected) != 0;
            case "GT" -> compare(actual, expected) > 0;
            case "GTE" -> compare(actual, expected) >= 0;
            case "LT" -> compare(actual, expected) < 0;
            case "LTE" -> compare(actual, expected) <= 0;
            case "CONTAINS" -> actual != null && expected != null
                    && actual.toString().contains(expected.toString());
            default -> throw new IllegalArgumentException("Unsupported condition operator: " + operator);
        };
    }

    private Object readPath(Map<String, Object> context, String path) {
        Object current = context;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
        }
        return current;
    }

    private int compare(Object left, Object right) {
        if (left == null && right == null) return 0;
        if (left == null) return -1;
        if (right == null) return 1;
        if (left instanceof Number || right instanceof Number) {
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()));
        }
        return left.toString().compareTo(right.toString());
    }

    private String onlyTarget(String nodeId, Map<String, List<WorkflowDefinition.Edge>> outgoing) {
        return outgoing.getOrDefault(nodeId, List.of()).getFirst().getTarget();
    }

    private void ensureActive(String executionId) {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Workflow execution interrupted");
        }
        WorkflowExecution current = getExecutionEntity(executionId);
        if (current == null || isStopStatus(current.getStatus())) {
            throw new CancellationException("Workflow execution is no longer active");
        }
    }

    private boolean isStopStatus(String status) {
        return "CANCELLED".equals(status) || "TIMEOUT".equals(status) || STATUS_DRY_CANCELLED.equals(status);
    }

    private void timeoutExecution(String executionId) {
        WorkflowExecution execution = getExecutionEntity(executionId);
        if (execution == null || TERMINAL_STATUSES.contains(execution.getStatus())) return;
        finish(execution, "TIMEOUT", null, "Execution timeout");
        Future<?> future = runningTasks.remove(executionId);
        if (future != null) future.cancel(true);
        timeoutTasks.remove(executionId);
    }

    private void finish(WorkflowExecution execution, String status, String result, String errorMessage) {
        WorkflowExecution current = getExecutionEntity(execution.getExecutionId());
        if (current != null && TERMINAL_STATUSES.contains(current.getStatus())) return;
        execution.setStatus(status);
        execution.setResult(result);
        execution.setErrorMessage(errorMessage);
        execution.setEndTime(LocalDateTime.now());
        executionMapper.updateById(execution);
    }

    private synchronized void persistContext(WorkflowExecution execution, String currentNode, Map<String, Object> context) {
        Map<String, Object> snapshot = new LinkedHashMap<>(context);
        snapshot.put("currentNode", currentNode);
        execution.setExecutionContext(writeJson(snapshot));
        executionMapper.updateById(execution);
    }

    private Map<String, Object> readStartParams(WorkflowExecution execution) {
        if (execution.getStartParams() == null || execution.getStartParams().isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(execution.getStartParams(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workflow start parameters", e);
        }
    }

    private Map<String, Object> readContext(WorkflowExecution execution) {
        if (execution.getExecutionContext() == null || execution.getExecutionContext().isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(execution.getExecutionContext(), new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize workflow data", e);
        }
    }

    private WorkflowAgentRunAdapter.WorkflowExecutionContext executionContext(
            WorkflowExecution execution, Workflow workflow) {
        return new WorkflowAgentRunAdapter.WorkflowExecutionContext() {
            @Override public String getExecutionId() { return execution.getExecutionId(); }
            @Override public String getProjectKey() { return workflow.getProjectKey(); }
            @Override public Long getWorkflowId() { return execution.getWorkflowId(); }
            @Override public Long getVersionId() { return execution.getVersionId(); }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractRunInputs(WorkflowExecution execution) {
        if (execution.getStartParams() == null || execution.getStartParams().isBlank()) return Map.of();
        try {
            Object parsed = objectMapper.readValue(execution.getStartParams(), Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> typed = new HashMap<>();
                map.forEach((k, v) -> typed.put(String.valueOf(k), v));
                return typed;
            }
        } catch (Exception e) {
            log.warn("Cannot parse start params for execution {}: {}", execution.getExecutionId(), e.getMessage());
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> extractNodeOutputs(Map<String, Object> context) {
        Map<String, Map<String, Object>> outputs = new HashMap<>();
        Object raw = context.get("nodeOutputs");
        if (raw instanceof Map<?, ?> map) {
            map.forEach((nodeId, value) -> {
                if (value instanceof Map<?, ?> nested) {
                    Map<String, Object> typed = new HashMap<>();
                    nested.forEach((k, v) -> typed.put(String.valueOf(k), v));
                    outputs.put(String.valueOf(nodeId), typed);
                }
            });
        }
        return outputs;
    }

    private List<WorkflowDefinition.Variable> definitionVariables(WorkflowDefinition definition) {
        return definition.getVariables() == null ? List.of() : definition.getVariables();
    }

    /**
     * Load the definition from the immutable snapshot this execution bound at creation.
     * Falls back to an in-memory freeze of the current draft only when no snapshot exists
     * (legacy rows created before version binding).
     */
    private WorkflowDefinition loadBoundDefinition(WorkflowExecution execution) {
        if (execution.getVersionId() != null) {
            WorkflowVersion version = workflowVersionService.requireActiveVersion(
                    execution.getWorkflowId(), execution.getVersionId());
            if (execution.getDefinitionHash() != null
                    && !execution.getDefinitionHash().equals(version.getDefinitionHash())) {
                throw ErrorCode.WORKFLOW_VERSION_NOT_ACTIVE.ex();
            }
            return definitionService.parseAndValidate(version.getDefinitionJson());
        }
        Workflow workflow = requireWorkflow(execution.getWorkflowId());
        return definitionService.parseAndValidate(workflow.getWorkflowDefinition());
    }

    private Workflow requireWorkflow(Long workflowId) {
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) throw new IllegalArgumentException("Workflow not found: " + workflowId);
        return workflow;
    }

    private WorkflowExecution requireExecution(String executionId) {
        WorkflowExecution execution = getExecutionEntity(executionId);
        if (execution == null) throw ErrorCode.WORKFLOW_EXECUTION_NOT_FOUND.ex();
        return execution;
    }

    private WorkflowExecution getExecutionEntity(String executionId) {
        return executionMapper.selectOne(new LambdaQueryWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getExecutionId, executionId));
    }

    private String normalizeTriggerType(String triggerType) {
        return triggerType == null || triggerType.isBlank() ? "MANUAL" : triggerType.trim().toUpperCase();
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void runWithTenantContext(TenantContext.Context context, Runnable runnable) {
        try {
            if (context != null) TenantContext.set(context);
            runnable.run();
        } finally {
            TenantContext.clear();
        }
    }

    private void cancelTimeout(String executionId) {
        ScheduledFuture<?> timeout = timeoutTasks.remove(executionId);
        if (timeout != null) timeout.cancel(false);
    }

    @PreDestroy
    public void shutdown() {
        runningTasks.values().forEach(task -> task.cancel(true));
        workflowExecutor.shutdownNow();
        timeoutScheduler.shutdownNow();
    }
}
