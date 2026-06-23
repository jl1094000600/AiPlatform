package com.aipal.service;

import cn.hutool.core.util.IdUtil;
import com.aipal.dto.A2AMessage;
import com.aipal.dto.WorkflowDefinition;
import com.aipal.dto.WorkflowStep;
import com.aipal.entity.Workflow;
import com.aipal.entity.WorkflowExecution;
import com.aipal.mapper.WorkflowExecutionMapper;
import com.aipal.mapper.WorkflowMapper;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final Set<String> TERMINAL_STATUSES = Set.of("COMPLETED", "FAILED", "CANCELLED", "TIMEOUT");

    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowMapper workflowMapper;
    private final ObjectMapper objectMapper;
    private final A2AMessageService a2aMessageService;
    private final AgentRegistry agentRegistry;
    private final WorkflowDefinitionService definitionService;
    private final MemoryOrchestrator memoryOrchestrator;

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
        String executionId = IdUtil.fastSimpleUUID();
        WorkflowExecution execution = new WorkflowExecution();
        execution.setTenantId(TenantContext.tenantId());
        execution.setExecutionId(executionId);
        execution.setWorkflowId(workflowId);
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
        Workflow workflow = requireWorkflow(execution.getWorkflowId());
        WorkflowDefinition definition = definitionService.parseAndValidate(workflow.getWorkflowDefinition());
        TenantContext.Context tenantContext = TenantContext.get();

        FutureTask<Void> future = new FutureTask<>(() -> {
            runWithTenantContext(tenantContext, () -> executeWorkflow(execution, definition));
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
        context.put(node.getId(), invokeAgent(execution, node, context));
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

    private void executeWorkflow(WorkflowExecution execution, WorkflowDefinition definition) {
        try {
            execution.setStatus("RUNNING");
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
            executeNode(execution, startId, nodes, outgoing, context);
            ensureActive(execution.getExecutionId());
            finish(execution, "COMPLETED", writeJson(context), null);
        } catch (CancellationException e) {
            WorkflowExecution current = getExecutionEntity(execution.getExecutionId());
            if (current != null && !TERMINAL_STATUSES.contains(current.getStatus())) {
                finish(current, "CANCELLED", null, "Execution cancelled");
            }
        } catch (Exception e) {
            Throwable cause = unwrap(e);
            WorkflowExecution current = getExecutionEntity(execution.getExecutionId());
            if (current != null && !TERMINAL_STATUSES.contains(current.getStatus())) {
                finish(current, "FAILED", null, cause.getMessage());
            }
            log.error("Workflow execution failed: {}", execution.getExecutionId(), cause);
        } finally {
            runningTasks.remove(execution.getExecutionId());
            cancelTimeout(execution.getExecutionId());
        }
    }

    private void executeNode(WorkflowExecution execution,
                             String nodeId,
                             Map<String, WorkflowDefinition.Node> nodes,
                             Map<String, List<WorkflowDefinition.Edge>> outgoing,
                             Map<String, Object> context) {
        ensureActive(execution.getExecutionId());
        WorkflowDefinition.Node node = nodes.get(nodeId);
        if (node == null) throw new IllegalStateException("Workflow node not found: " + nodeId);
        persistContext(execution, nodeId, context);

        switch (node.getType()) {
            case "START" -> executeNode(execution, onlyTarget(nodeId, outgoing), nodes, outgoing, context);
            case "AGENT" -> {
                context.put(nodeId, invokeAgent(execution, node, context));
                persistContext(execution, nodeId, context);
                executeNode(execution, onlyTarget(nodeId, outgoing), nodes, outgoing, context);
            }
            case "CONDITION" -> executeNode(execution,
                    conditionTarget(node, outgoing.getOrDefault(nodeId, List.of()), context),
                    nodes, outgoing, context);
            case "PARALLEL" -> executeParallel(execution, nodeId, nodes, outgoing, context);
            case "END" -> context.put("completedNode", nodeId);
            default -> throw new IllegalStateException("Unsupported workflow node type: " + node.getType());
        }
    }

    private void executeParallel(WorkflowExecution execution,
                                 String nodeId,
                                 Map<String, WorkflowDefinition.Node> nodes,
                                 Map<String, List<WorkflowDefinition.Edge>> outgoing,
                                 Map<String, Object> context) {
        TenantContext.Context tenantContext = TenantContext.get();
        List<CompletableFuture<Map.Entry<String, Map<String, Object>>>> branches = outgoing
                .getOrDefault(nodeId, List.of()).stream()
                .map(edge -> CompletableFuture.supplyAsync(() -> {
                    Map<String, Object> branchContext = new HashMap<>(context);
                    runWithTenantContext(tenantContext,
                            () -> executeNode(execution, edge.getTarget(), nodes, outgoing, branchContext));
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

    private Object invokeAgent(WorkflowExecution execution, WorkflowDefinition.Node node,
                               Map<String, Object> context) {
        AgentRegistry.AgentContext agent = resolveAgent(node);
        Map<String, Object> payload = new HashMap<>();
        if (node.getParams() != null) payload.putAll(node.getParams());
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
                return response.getPayload();
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
        if (current == null || "CANCELLED".equals(current.getStatus()) || "TIMEOUT".equals(current.getStatus())) {
            throw new CancellationException("Workflow execution is no longer active");
        }
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

    private Workflow requireWorkflow(Long workflowId) {
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) throw new IllegalArgumentException("Workflow not found: " + workflowId);
        return workflow;
    }

    private WorkflowExecution requireExecution(String executionId) {
        WorkflowExecution execution = getExecutionEntity(executionId);
        if (execution == null) throw new IllegalArgumentException("Workflow execution not found: " + executionId);
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
