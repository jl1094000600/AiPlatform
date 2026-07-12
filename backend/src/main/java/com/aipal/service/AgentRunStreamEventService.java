package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.agent.runtime.AgentRunStreamEventType;
import com.aipal.entity.AgentRunStreamEvent;
import com.aipal.mapper.AgentRunStreamEventMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentRunStreamEventService {
    private final AgentRunStreamEventMapper streamEventMapper;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized AgentRunStreamEvent record(Long tenantId, Long runId, AgentRunStreamEventType type,
                                                   Map<String, Object> payload, boolean visibleToBusiness) {
        DuplicateKeyException lastDuplicate = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                AgentRunStreamEvent event = newEvent(tenantId, runId, type, payload, visibleToBusiness);
                streamEventMapper.insert(event);
                return event;
            } catch (DuplicateKeyException duplicate) {
                lastDuplicate = duplicate;
            }
        }
        throw lastDuplicate == null ? new IllegalStateException("Failed to record run stream event") : lastDuplicate;
    }

    private AgentRunStreamEvent newEvent(Long tenantId, Long runId, AgentRunStreamEventType type,
                                         Map<String, Object> payload, boolean visibleToBusiness) {
        AgentRunStreamEvent event = new AgentRunStreamEvent();
        event.setTenantId(tenantId);
        event.setRunId(runId);
        event.setEventType(type.name());
        event.setSequenceNo(nextSequenceNo(tenantId, runId));
        event.setPayloadJson(json(payload == null ? Map.of() : payload));
        event.setVisibleToBusiness(visibleToBusiness ? 1 : 0);
        event.setCreateTime(LocalDateTime.now());
        event.setIsDeleted(0);
        return event;
    }

    public List<RunStreamEventView> listForRun(Long tenantId, Long runId, Long afterSequenceNo, int limit,
                                               boolean includeRestricted) {
        int cappedLimit = Math.max(1, Math.min(limit, 500));
        LambdaQueryWrapper<AgentRunStreamEvent> query = new LambdaQueryWrapper<AgentRunStreamEvent>()
                .eq(AgentRunStreamEvent::getTenantId, tenantId)
                .eq(AgentRunStreamEvent::getRunId, runId)
                .gt(afterSequenceNo != null && afterSequenceNo > 0, AgentRunStreamEvent::getSequenceNo, afterSequenceNo)
                .eq(!includeRestricted, AgentRunStreamEvent::getVisibleToBusiness, 1)
                .orderByAsc(AgentRunStreamEvent::getSequenceNo)
                .last("LIMIT " + cappedLimit);
        return streamEventMapper.selectList(query).stream().map(this::toView).toList();
    }

    private long nextSequenceNo(Long tenantId, Long runId) {
        Long max = streamEventMapper.selectMaxSequenceNo(tenantId, runId);
        return max == null ? 1L : max + 1L;
    }

    private RunStreamEventView toView(AgentRunStreamEvent event) {
        return new RunStreamEventView(event.getId(), event.getRunId(), event.getEventType(), event.getSequenceNo(),
                parsePayload(event.getPayloadJson()), event.getVisibleToBusiness() != null && event.getVisibleToBusiness() == 1,
                event.getCreateTime());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Stream event payload cannot be serialized", e);
        }
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new BizException(500, "Run stream event payload cannot be read");
        }
    }

    public record RunStreamEventView(Long id, Long runId, String eventType, Long sequenceNo,
                                     Map<String, Object> payload, Boolean visibleToBusiness,
                                     LocalDateTime createTime) {
    }
}
