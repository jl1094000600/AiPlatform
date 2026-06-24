package com.aipal.service.memory;

import com.aipal.common.BizException;
import com.aipal.entity.AiMemoryItem;
import com.aipal.entity.AiMemoryRecallTrace;
import com.aipal.entity.AiMemoryVersion;
import com.aipal.mapper.AiMemoryItemMapper;
import com.aipal.mapper.AiMemoryRecallTraceMapper;
import com.aipal.mapper.AiMemoryVersionMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryManagementService {

    private final AiMemoryItemMapper memoryItemMapper;
    private final AiMemoryVersionMapper memoryVersionMapper;
    private final AiMemoryRecallTraceMapper traceMapper;
    private final MemoryAccessScopeResolver accessScopeResolver;
    private final MemoryLifecycleService lifecycleService;

    public Page<AiMemoryItem> list(int pageNum, int pageSize, String memoryType, String status) {
        MemoryAccessScope scope = accessScopeResolver.resolve(null);
        LambdaQueryWrapper<AiMemoryItem> query = accessibleQuery(scope)
                .eq(memoryType != null && !memoryType.isBlank(), AiMemoryItem::getMemoryType, memoryType)
                .eq(status != null && !status.isBlank(), AiMemoryItem::getStatus, status)
                .orderByDesc(AiMemoryItem::getUpdateTime);
        return memoryItemMapper.selectPage(new Page<>(pageNum, pageSize), query);
    }

    public AiMemoryItem get(Long id) {
        AiMemoryItem memory = memoryItemMapper.selectById(id);
        assertReadable(memory);
        return memory;
    }

    public AiMemoryItem update(Long id, Integer expectedVersion, String title, String content, String reason) {
        assertWritable(get(id));
        return lifecycleService.updateContent(id, expectedVersion, title, content, reason);
    }

    public AiMemoryItem forget(Long id, String reason) {
        assertWritable(get(id));
        return lifecycleService.forget(id, reason);
    }

    public AiMemoryItem confirm(Long id) {
        assertWritable(get(id));
        return lifecycleService.confirm(id);
    }

    public List<AiMemoryVersion> versions(Long id) {
        get(id);
        return memoryVersionMapper.selectList(new LambdaQueryWrapper<AiMemoryVersion>()
                .eq(AiMemoryVersion::getMemoryId, id)
                .orderByDesc(AiMemoryVersion::getVersion));
    }

    public AiMemoryRecallTrace trace(String traceId) {
        AiMemoryRecallTrace trace = traceMapper.selectOne(new LambdaQueryWrapper<AiMemoryRecallTrace>()
                .eq(AiMemoryRecallTrace::getTraceId, traceId)
                .last("LIMIT 1"));
        if (trace == null || !TenantContext.tenantId().equals(trace.getTenantId())) {
            throw new BizException(404, "记忆调用记录不存在");
        }
        if (!TenantContext.hasPermission("memory:policy")
                && (TenantContext.userId() == null || !TenantContext.userId().equals(trace.getUserId()))) {
            throw new BizException(403, "无权查看该记忆调用记录");
        }
        return trace;
    }

    public Page<AiMemoryRecallTrace> traces(int pageNum, int pageSize, String recallMode) {
        LambdaQueryWrapper<AiMemoryRecallTrace> query = new LambdaQueryWrapper<AiMemoryRecallTrace>()
                .eq(AiMemoryRecallTrace::getTenantId, TenantContext.tenantId())
                .eq(recallMode != null && !recallMode.isBlank(), AiMemoryRecallTrace::getRecallMode, recallMode)
                .orderByDesc(AiMemoryRecallTrace::getCreateTime);
        if (!TenantContext.hasPermission("memory:policy") && TenantContext.userId() != null) {
            query.eq(AiMemoryRecallTrace::getUserId, TenantContext.userId());
        }
        return traceMapper.selectPage(new Page<>(pageNum, pageSize), query);
    }

    private LambdaQueryWrapper<AiMemoryItem> accessibleQuery(MemoryAccessScope scope) {
        LambdaQueryWrapper<AiMemoryItem> query = new LambdaQueryWrapper<AiMemoryItem>()
                .eq(AiMemoryItem::getTenantId, scope.tenantId());
        if (scope.canManageTenantMemory()) return query;
        return query.and(wrapper -> wrapper.eq(AiMemoryItem::getScopeType, "TENANT")
                .or(user -> user.eq(AiMemoryItem::getScopeType, "USER")
                        .eq(AiMemoryItem::getOwnerUserId, scope.userId())));
    }

    private void assertReadable(AiMemoryItem memory) {
        if (memory == null || !TenantContext.tenantId().equals(memory.getTenantId())) {
            throw new BizException(404, "记忆不存在");
        }
        if (TenantContext.hasPermission("memory:policy")) return;
        boolean tenantVisible = "TENANT".equals(memory.getScopeType());
        boolean ownedByCurrentUser = "USER".equals(memory.getScopeType())
                && TenantContext.userId() != null && TenantContext.userId().equals(memory.getOwnerUserId());
        if (!tenantVisible && !ownedByCurrentUser) throw new BizException(403, "无权访问该记忆");
    }

    private void assertWritable(AiMemoryItem memory) {
        if (TenantContext.hasPermission("memory:policy")) return;
        boolean ownedByCurrentUser = "USER".equals(memory.getScopeType())
                && TenantContext.userId() != null && TenantContext.userId().equals(memory.getOwnerUserId());
        if (!ownedByCurrentUser) throw new BizException(403, "只能修改自己的记忆");
    }
}
