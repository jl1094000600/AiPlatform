package com.aipal.service;

import com.aipal.agent.runtime.AgentToolPermissionDecision;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentToolDefinition;
import com.aipal.entity.AgentToolPolicy;
import com.aipal.mapper.AgentToolPolicyMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolPermissionEvaluatorTest {

    @Test
    void defaultsToDenyWhenNoPolicyMatches() {
        AgentToolPolicyMapper mapper = mock(AgentToolPolicyMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        AgentToolPermissionEvaluator evaluator = new AgentToolPermissionEvaluator(mapper);

        AgentToolPermissionEvaluator.ToolPermissionResult result = evaluator.evaluate(run(), definition("memory.search", 0));

        assertEquals(AgentToolPermissionDecision.DENY, result.decision());
        assertEquals("READ_ONLY", result.riskLevel());
    }

    @Test
    void choosesMostSpecificPolicy() {
        AgentToolPolicyMapper mapper = mock(AgentToolPolicyMapper.class);
        AgentToolPolicy tenantPolicy = policy(1L, 0L, 0L, "DENY", 0);
        AgentToolPolicy versionPolicy = policy(2L, 11L, 22L, "ALLOW", 0);
        when(mapper.selectList(any())).thenReturn(List.of(tenantPolicy, versionPolicy));
        AgentToolPermissionEvaluator evaluator = new AgentToolPermissionEvaluator(mapper);

        AgentToolPermissionEvaluator.ToolPermissionResult result = evaluator.evaluate(run(), definition("memory.search", 0));

        assertEquals(AgentToolPermissionDecision.ALLOW, result.decision());
        assertEquals(2L, result.policyId());
    }

    @Test
    void allowWithApprovalRequirementDoesNotExecuteImmediately() {
        AgentToolPolicyMapper mapper = mock(AgentToolPolicyMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(policy(3L, 11L, 22L, "ALLOW", 1)));
        AgentToolPermissionEvaluator evaluator = new AgentToolPermissionEvaluator(mapper);

        AgentToolPermissionEvaluator.ToolPermissionResult result = evaluator.evaluate(run(), definition("artifact.create", 1));

        assertEquals(AgentToolPermissionDecision.REQUIRE_APPROVAL, result.decision());
        assertTrue(result.requiresApproval());
    }

    private AgentRun run() {
        AgentRun run = new AgentRun();
        run.setTenantId(1L);
        run.setAgentId(11L);
        run.setAgentVersionId(22L);
        return run;
    }

    private AgentToolDefinition definition(String toolName, int requiresApproval) {
        AgentToolDefinition definition = new AgentToolDefinition();
        definition.setToolName(toolName);
        definition.setRiskLevel("READ_ONLY");
        definition.setRequiresApproval(requiresApproval);
        return definition;
    }

    private AgentToolPolicy policy(Long id, Long agentId, Long versionId, String decision, int requiresApproval) {
        AgentToolPolicy policy = new AgentToolPolicy();
        policy.setId(id);
        policy.setTenantId(1L);
        policy.setAgentId(agentId);
        policy.setAgentVersionId(versionId);
        policy.setToolName("memory.search");
        policy.setDecision(decision);
        policy.setRiskLevel("READ_ONLY");
        policy.setRequiresApproval(requiresApproval);
        policy.setStatus(1);
        return policy;
    }
}
