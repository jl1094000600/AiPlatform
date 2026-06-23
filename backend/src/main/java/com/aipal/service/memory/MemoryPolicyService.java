package com.aipal.service.memory;

import com.aipal.entity.AiMemoryPolicy;
import com.aipal.mapper.AiMemoryPolicyMapper;
import com.aipal.memory.MemoryScopeType;
import com.aipal.memory.RecallMode;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemoryPolicyService {

    private final AiMemoryPolicyMapper policyMapper;
    private final MemoryAccessScopeResolver accessScopeResolver;

    public AiMemoryPolicy resolveEffectivePolicy(String ignoredRequestedProjectKey) {
        Long tenantId = TenantContext.tenantId();
        String projectKey = accessScopeResolver.resolve(null).projectKey();
        if (projectKey != null && !projectKey.isBlank()) {
            AiMemoryPolicy projectPolicy = policyMapper.selectOne(new LambdaQueryWrapper<AiMemoryPolicy>()
                    .eq(AiMemoryPolicy::getTenantId, tenantId)
                    .eq(AiMemoryPolicy::getScopeType, MemoryScopeType.PROJECT.name())
                    .eq(AiMemoryPolicy::getScopeKey, projectKey.trim())
                    .eq(AiMemoryPolicy::getEnabled, 1)
                    .last("LIMIT 1"));
            if (projectPolicy != null) return projectPolicy;
        }

        AiMemoryPolicy tenantPolicy = policyMapper.selectOne(new LambdaQueryWrapper<AiMemoryPolicy>()
                .eq(AiMemoryPolicy::getTenantId, tenantId)
                .eq(AiMemoryPolicy::getScopeType, MemoryScopeType.TENANT.name())
                .eq(AiMemoryPolicy::getScopeKey, tenantScopeKey(tenantId))
                .eq(AiMemoryPolicy::getEnabled, 1)
                .last("LIMIT 1"));
        return tenantPolicy == null ? defaultPolicy(tenantId) : tenantPolicy;
    }

    @Transactional
    public AiMemoryPolicy save(AiMemoryPolicy policy) {
        Long tenantId = TenantContext.tenantId();
        policy.setTenantId(tenantId);
        policy.setScopeType(defaultValue(policy.getScopeType(), MemoryScopeType.TENANT.name()));
        policy.setScopeKey(defaultValue(policy.getScopeKey(), tenantScopeKey(tenantId)));
        policy.setEnabled(policy.getEnabled() == null ? 1 : policy.getEnabled());
        policy.setRecallMode(defaultValue(policy.getRecallMode(), RecallMode.AUDIT.name()));
        policy.setRetentionDays(defaultPositive(policy.getRetentionDays(), 180));
        policy.setSessionTokenBudget(defaultNonNegative(policy.getSessionTokenBudget(), 800));
        policy.setWorkingTokenBudget(defaultNonNegative(policy.getWorkingTokenBudget(), 300));
        policy.setLongTermTokenBudget(defaultNonNegative(policy.getLongTermTokenBudget(), 500));
        policy.setProjectTokenBudget(defaultNonNegative(policy.getProjectTokenBudget(), 400));
        policy.setVectorEnabled(policy.getVectorEnabled() == null ? 0 : policy.getVectorEnabled());

        if (policy.getId() == null) {
            policy.setPolicyVersion(1);
            policyMapper.insert(policy);
        } else {
            AiMemoryPolicy existing = policyMapper.selectById(policy.getId());
            if (existing == null) throw new IllegalArgumentException("Memory policy does not exist");
            policy.setPolicyVersion((existing.getPolicyVersion() == null ? 0 : existing.getPolicyVersion()) + 1);
            policyMapper.updateById(policy);
        }
        return policy;
    }

    private AiMemoryPolicy defaultPolicy(Long tenantId) {
        AiMemoryPolicy policy = new AiMemoryPolicy();
        policy.setTenantId(tenantId);
        policy.setScopeType(MemoryScopeType.TENANT.name());
        policy.setScopeKey(tenantScopeKey(tenantId));
        policy.setPolicyVersion(0);
        policy.setEnabled(1);
        policy.setRecallMode(RecallMode.AUDIT.name());
        policy.setRetentionDays(180);
        policy.setMaxSensitivity("INTERNAL");
        policy.setVectorEnabled(0);
        policy.setSessionTokenBudget(800);
        policy.setWorkingTokenBudget(300);
        policy.setLongTermTokenBudget(500);
        policy.setProjectTokenBudget(400);
        return policy;
    }

    private String tenantScopeKey(Long tenantId) {
        return "tenant:" + tenantId;
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Integer defaultPositive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private Integer defaultNonNegative(Integer value, int fallback) {
        return value == null || value < 0 ? fallback : value;
    }
}
