package com.aipal.service.memory;

import com.aipal.entity.AiMemoryItem;
import com.aipal.entity.AiMemoryVersion;
import com.aipal.mapper.AiMemoryItemMapper;
import com.aipal.mapper.AiMemoryVersionMapper;
import com.aipal.memory.MemoryStatus;
import com.aipal.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemoryLifecycleService {

    private final AiMemoryItemMapper memoryItemMapper;
    private final AiMemoryVersionMapper memoryVersionMapper;

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
