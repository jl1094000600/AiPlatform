package com.aipal.service.memory;

import com.aipal.entity.AiMemoryItem;
import com.aipal.entity.AiUserMemory;
import com.aipal.mapper.AiMemoryItemMapper;
import com.aipal.mapper.AiUserMemoryMapper;
import com.aipal.memory.MemoryScopeType;
import com.aipal.memory.MemorySourceType;
import com.aipal.memory.MemoryStatus;
import com.aipal.memory.MemoryType;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LegacyUserMemoryMigrationService {

    private final AiUserMemoryMapper legacyMemoryMapper;
    private final AiMemoryItemMapper memoryItemMapper;

    /**
     * Imports legacy compressed summaries into a review-only state. It is safe to
     * invoke multiple times and never overwrites an existing migrated item.
     */
    @Transactional
    public int migrateCurrentTenant() {
        Long tenantId = TenantContext.tenantId();
        List<AiUserMemory> legacyMemories = legacyMemoryMapper.selectList(
                new LambdaQueryWrapper<AiUserMemory>().orderByAsc(AiUserMemory::getId));
        int migrated = 0;
        for (AiUserMemory legacy : legacyMemories) {
            Long count = memoryItemMapper.selectCount(new LambdaQueryWrapper<AiMemoryItem>()
                    .eq(AiMemoryItem::getTenantId, tenantId)
                    .eq(AiMemoryItem::getLegacyMemoryId, legacy.getId()));
            if (count != null && count > 0) continue;
            memoryItemMapper.insert(toMemoryItem(legacy, tenantId));
            migrated++;
        }
        return migrated;
    }

    private AiMemoryItem toMemoryItem(AiUserMemory legacy, Long tenantId) {
        AiMemoryItem item = new AiMemoryItem();
        item.setTenantId(tenantId);
        item.setMemoryCode("LEGACY_MEM_" + tenantId + "_" + legacy.getId());
        item.setMemoryType(MemoryType.PIPELINE_SUMMARY.name());
        item.setScopeType(legacy.getUserId() == null ? MemoryScopeType.TENANT.name() : MemoryScopeType.USER.name());
        item.setScopeKey(legacy.getUserId() == null ? "tenant:" + tenantId : "user:" + legacy.getUserId());
        item.setOwnerUserId(legacy.getUserId());
        item.setOwnerUsername(legacy.getUsername());
        item.setTitle("历史流水线记忆 " + legacy.getMemoryCode());
        item.setContent(legacy.getSummaryContent());
        item.setSourceType(MemorySourceType.LEGACY.name());
        item.setSourceRef(legacy.getMemoryCode());
        item.setLegacyMemoryId(legacy.getId());
        item.setSensitivity("INTERNAL");
        item.setImportance(50);
        item.setConfidence(BigDecimal.valueOf(0.6));
        item.setStatus(MemoryStatus.PENDING_REVIEW.name());
        item.setVersion(1);
        item.setValidFrom(legacy.getMemoryStartTime());
        item.setCreateTime(legacy.getCreateTime());
        item.setUpdateTime(legacy.getUpdateTime());
        item.setCreatedBy("legacy-migration");
        item.setIsDeleted(0);
        return item;
    }
}
