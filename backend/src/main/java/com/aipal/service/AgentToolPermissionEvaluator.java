package com.aipal.service;

import com.aipal.agent.runtime.AgentToolPermissionDecision;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentToolDefinition;
import com.aipal.entity.AgentToolPolicy;
import com.aipal.mapper.AgentToolPolicyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentToolPermissionEvaluator {
    private static final long WILDCARD = 0L;

    private final AgentToolPolicyMapper policyMapper;

    public ToolPermissionResult evaluate(AgentRun run, AgentToolDefinition definition) {
        if (run == null || definition == null || definition.getToolName() == null) {
            return denied(definition == null ? null : definition.getRiskLevel());
        }
        List<AgentToolPolicy> policies = policyMapper.selectList(new LambdaQueryWrapper<AgentToolPolicy>()
                .eq(AgentToolPolicy::getTenantId, run.getTenantId())
                .eq(AgentToolPolicy::getToolName, definition.getToolName())
                .eq(AgentToolPolicy::getStatus, 1)
                .in(AgentToolPolicy::getAgentId, List.of(WILDCARD, defaultLong(run.getAgentId())))
                .in(AgentToolPolicy::getAgentVersionId, List.of(WILDCARD, defaultLong(run.getAgentVersionId()))));
        AgentToolPolicy policy = policies.stream()
                .max(Comparator.comparingInt(candidate -> specificity(candidate, run)))
                .orElse(null);
        if (policy == null) return denied(definition.getRiskLevel());

        AgentToolPermissionDecision decision = parseDecision(policy.getDecision());
        String riskLevel = blank(policy.getRiskLevel()) ? definition.getRiskLevel() : policy.getRiskLevel();
        boolean requiresApproval = truthy(policy.getRequiresApproval()) || truthy(definition.getRequiresApproval());
        if (decision == AgentToolPermissionDecision.ALLOW && requiresApproval) {
            decision = AgentToolPermissionDecision.REQUIRE_APPROVAL;
        }
        return new ToolPermissionResult(decision, riskLevel, requiresApproval, policy.getId());
    }

    private ToolPermissionResult denied(String riskLevel) {
        return new ToolPermissionResult(AgentToolPermissionDecision.DENY, riskLevel, false, null);
    }

    private AgentToolPermissionDecision parseDecision(String value) {
        if (blank(value)) return AgentToolPermissionDecision.DENY;
        try {
            return AgentToolPermissionDecision.valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return AgentToolPermissionDecision.DENY;
        }
    }

    private int specificity(AgentToolPolicy policy, AgentRun run) {
        int score = 0;
        if (same(policy.getAgentId(), run.getAgentId())) score += 2;
        if (same(policy.getAgentVersionId(), run.getAgentVersionId())) score += 4;
        return score;
    }

    private boolean same(Long left, Long right) {
        return left != null && right != null && left.equals(right);
    }

    private long defaultLong(Long value) {
        return value == null ? WILDCARD : value;
    }

    private boolean truthy(Integer value) {
        return value != null && value == 1;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record ToolPermissionResult(AgentToolPermissionDecision decision, String riskLevel,
                                       boolean requiresApproval, Long policyId) {
    }
}
