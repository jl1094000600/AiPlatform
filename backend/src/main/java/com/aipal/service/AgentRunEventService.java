package com.aipal.service;

import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentRunEvent;
import com.aipal.mapper.AgentRunEventMapper;
import com.aipal.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentRunEventService {
    private final AgentRunEventMapper eventMapper;

    public void record(AgentRun run, String fromStatus, String toStatus, String reason) {
        if (run == null || run.getId() == null || toStatus == null || toStatus.isBlank()) return;
        TenantContext.Context context = TenantContext.get();
        AgentRunEvent event = new AgentRunEvent();
        event.setTenantId(run.getTenantId());
        event.setRunId(run.getId());
        event.setFromStatus(blank(fromStatus) ? null : fromStatus.trim());
        event.setToStatus(toStatus.trim());
        event.setActorUserId(context == null ? null : context.userId());
        event.setActorName(context == null || blank(context.username()) ? "system" : truncate(context.username(), 128));
        event.setReason(truncate(reason, 512));
        event.setTraceId(truncate(run.getTraceId(), 96));
        event.setCreateTime(LocalDateTime.now());
        event.setIsDeleted(0);
        eventMapper.insert(event);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), max));
    }
}
