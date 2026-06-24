package com.aipal.service.memory;

import com.aipal.entity.AiMemoryItem;
import com.aipal.entity.AiMemoryVersion;
import com.aipal.mapper.AiMemoryItemMapper;
import com.aipal.mapper.AiMemoryVersionMapper;
import com.aipal.memory.MemoryStatus;
import com.aipal.security.TenantContext;
import com.aipal.security.TenantTaskRunner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemoryLifecycleService {

    private final AiMemoryItemMapper memoryItemMapper;
    private final AiMemoryVersionMapper memoryVersionMapper;
    private final TenantTaskRunner tenantTaskRunner;
    private final MemoryPolicyService policyService;
    private final MemoryVectorProjectionService vectorProjectionService;

    @Scheduled(fixedDelayString = "${aipal.memory.expiry-delay-ms:3600000}", initialDelayString = "${aipal.memory.expiry-initial-delay-ms:300000}")
    public void expireDueMemories() {
        tenantTaskRunner.forEachActiveTenant("memory-expiry", tenant -> expireCurrentTenant());
    }

    public int expireCurrentTenant() {
        java.util.List<AiMemoryItem> expired = memoryItemMapper.selectList(new LambdaQueryWrapper<AiMemoryItem>()
                .eq(AiMemoryItem::getTenantId, TenantContext.tenantId())
                .eq(AiMemoryItem::getStatus, MemoryStatus.ACTIVE.name())
                .isNotNull(AiMemoryItem::getExpiresAt)
                .le(AiMemoryItem::getExpiresAt, LocalDateTime.now()));
        for (AiMemoryItem memory : expired) {
            memory.setStatus(MemoryStatus.EXPIRED.name());
            memoryItemMapper.updateById(memory);
            vectorProjectionService.delete(memory);
        }
        return expired.size();
    }

    @Transactional
    public AiMemoryItem forget(Long memoryId, String reason) {
        AiMemoryItem memory = requireCurrentTenantMemory(memoryId);
        if (MemoryStatus.FORGOTTEN.name().equals(memory.getStatus())) {
            return memory;
        }

        memory.setVersion((memory.getVersion() == null ? 0 : memory.getVersion()) + 1);
        memory.setStatus(MemoryStatus.FORGOTTEN.name());
        memory.setExpiresAt(LocalDateTime.now());
        memoryItemMapper.updateById(memory);
        writeVersion(memory, "FORGET", reason);
        vectorProjectionService.delete(memory);
        return memory;
    }

    @Transactional
    public AiMemoryItem updateContent(Long memoryId, Integer expectedVersion, String title, String content, String reason) {
        AiMemoryItem memory = requireCurrentTenantMemory(memoryId);
        if (expectedVersion == null || !expectedVersion.equals(memory.getVersion())) {
            throw new IllegalStateException("Memory was changed by another request; refresh and retry");
        }
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw new IllegalArgumentException("Memory title and content are required");
        }
        memory.setTitle(title.trim());
        memory.setContent(content.trim());
        memory.setVersion(memory.getVersion() + 1);
        memoryItemMapper.updateById(memory);
        writeVersion(memory, "EDIT", reason);
        vectorProjectionService.projectIfAllowed(memory, policyService.resolveEffectivePolicy(null));
        return memory;
    }

    @Transactional
    public AiMemoryItem confirm(Long memoryId) {
        AiMemoryItem memory = requireCurrentTenantMemory(memoryId);
        if (MemoryStatus.ACTIVE.name().equals(memory.getStatus())) return memory;
        if (!MemoryStatus.PENDING_REVIEW.name().equals(memory.getStatus())) {
            throw new IllegalStateException("Only pending memories can be confirmed");
        }
        memory.setStatus(MemoryStatus.ACTIVE.name());
        memory.setVersion((memory.getVersion() == null ? 0 : memory.getVersion()) + 1);
        memoryItemMapper.updateById(memory);
        writeVersion(memory, "CONFIRM", "Confirmed for recall");
        vectorProjectionService.projectIfAllowed(memory, policyService.resolveEffectivePolicy(null));
        return memory;
    }

    private AiMemoryItem requireCurrentTenantMemory(Long memoryId) {
        if (memoryId == null) throw new IllegalArgumentException("Memory id is required");
        AiMemoryItem memory = memoryItemMapper.selectById(memoryId);
        if (memory == null || !TenantContext.tenantId().equals(memory.getTenantId())) {
            throw new IllegalArgumentException("Memory does not exist");
        }
        return memory;
    }

    private void writeVersion(AiMemoryItem memory, String changeType, String reason) {
        AiMemoryVersion version = new AiMemoryVersion();
        version.setTenantId(memory.getTenantId());
        version.setMemoryId(memory.getId());
        version.setVersion(memory.getVersion());
        version.setTitle(memory.getTitle());
        version.setContent(memory.getContent());
        version.setFactJson(memory.getFactJson());
        version.setStatus(memory.getStatus());
        version.setChangeType(changeType);
        version.setChangeReason(reason == null || reason.isBlank() ? null : reason.trim());
        version.setChangedBy(TenantContext.username());
        version.setCreateTime(LocalDateTime.now());
        memoryVersionMapper.insert(version);
    }
}
