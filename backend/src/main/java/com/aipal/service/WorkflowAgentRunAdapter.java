package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.common.ErrorCode;
import com.aipal.dto.WorkflowDefinition;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentTask;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter from workflow AGENT nodes onto the AgentRun pipeline.
 *
 * Contract (see WORKFLOW-GRAPH-PROTOCOL-DESIGN):
 * - businessId = {@code executionId:nodeId} is the stable idempotency identity
 * - resolved inputs are frozen into the run at creation
 * - retries reuse the same businessId so the run-level idempotency key dedupes
 * - outputsSchema is checked after execution and fails the node on mismatch
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowAgentRunAdapter {

    private static final String BUSINESS_TYPE = "WORKFLOW_NODE";

    private final AgentRunService agentRunService;
    private final AgentTaskService agentTaskService;
    private final AgentRunExecutionService executionService;
    private final AgentRunMapper runMapper;
    private final WorkflowVariableResolver variableResolver;
    private final ObjectMapper objectMapper;

    /**
     * Resolve node inputs and execute them through AgentRun.
     *
     * @return the node outputs map (already schema-checked when outputsSchema is present)
     */
    public Map<String, Object> invoke(WorkflowExecutionContext execution,
                                      WorkflowDefinition.Node node,
                                      List<WorkflowDefinition.Variable> variables,
                                      Map<String, Object> runInputs,
                                      Map<String, Map<String, Object>> nodeOutputs) {
        Map<String, Object> resolvedInputs = variableResolver.resolveInputs(
                node.getInputs(), variables, runInputs, nodeOutputs);

        String businessId = execution.getExecutionId() + ":" + node.getId();
        AgentRunService.CreateRunCommand command = new AgentRunService.CreateRunCommand(
                node.getAgentId(),
                execution.getProjectKey(),
                BUSINESS_TYPE,
                businessId,
                resolvedInputs);

        AgentRun run = agentRunService.createOrReuse(command);
        if (isTerminal(run.getStatus())) {
            return readOutputs(node, run);
        }

        AgentTask task = agentTaskService.claimRootTask(run.getId(), workerId(execution), 60);
        if (task == null) {
            // Another worker owns the run, or it already finished while we waited.
            run = agentRunService.get(run.getId());
            return readOutputs(node, run);
        }
        executionService.execute(task, task.getLeaseOwner());
        run = agentRunService.get(run.getId());
        return readOutputs(node, run);
    }

    private Map<String, Object> readOutputs(WorkflowDefinition.Node node, AgentRun run) {
        if (!AgentRunStatus.SUCCEEDED.name().equals(run.getStatus())) {
            throw new IllegalStateException("Agent node failed with run status "
                    + run.getStatus() + ": " + node.getId());
        }
        Map<String, Object> outputs = parseResult(run.getResultJson());
        validateOutputsSchema(node, outputs);
        return outputs;
    }

    private void validateOutputsSchema(WorkflowDefinition.Node node, Map<String, Object> outputs) {
        if (node.getOutputsSchema() == null || node.getOutputsSchema().isNull()) {
            return;
        }
        var required = node.getOutputsSchema().path("required");
        if (!required.isArray()) return;
        for (var field : required) {
            String name = field.asText();
            if (name != null && !name.isBlank() && !outputs.containsKey(name)) {
                throw new IllegalStateException("Agent node output missing required field '"
                        + name + "': " + node.getId());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResult(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) return new LinkedHashMap<>();
        try {
            Object parsed = objectMapper.readValue(resultJson, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> typed = new LinkedHashMap<>();
                map.forEach((k, v) -> typed.put(String.valueOf(k), v));
                return typed;
            }
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("output", parsed);
            return wrapped;
        } catch (Exception e) {
            throw ErrorCode.AGENT_INPUT_NOT_SERIALIZABLE.ex();
        }
    }

    private boolean isTerminal(String status) {
        return AgentRunStatus.SUCCEEDED.name().equals(status)
                || AgentRunStatus.FAILED.name().equals(status)
                || AgentRunStatus.CANCELLED.name().equals(status);
    }

    private String workerId(WorkflowExecutionContext execution) {
        return "workflow-" + execution.getExecutionId();
    }

    /** Minimal view of the running workflow node needs from its parent execution. */
    public interface WorkflowExecutionContext {
        String getExecutionId();
        String getProjectKey();
        Long getWorkflowId();
        Long getVersionId();
    }
}
