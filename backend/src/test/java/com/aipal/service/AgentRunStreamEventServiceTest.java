package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStreamEventType;
import com.aipal.entity.AgentRunStreamEvent;
import com.aipal.mapper.AgentRunStreamEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunStreamEventServiceTest {

    @Test
    void recordsNextSequenceAndBusinessVisibility() {
        AgentRunStreamEventMapper mapper = mock(AgentRunStreamEventMapper.class);
        when(mapper.selectMaxSequenceNo(1L, 7L)).thenReturn(2L);
        ArgumentCaptor<AgentRunStreamEvent> captor = ArgumentCaptor.forClass(AgentRunStreamEvent.class);
        when(mapper.insert(captor.capture())).thenReturn(1);
        AgentRunStreamEventService service = new AgentRunStreamEventService(mapper, new ObjectMapper());

        AgentRunStreamEvent event = service.record(1L, 7L, AgentRunStreamEventType.TOOL_COMPLETED,
                Map.of("toolName", "memory.search"), true);

        assertEquals(3L, event.getSequenceNo());
        assertEquals("TOOL_COMPLETED", captor.getValue().getEventType());
        assertEquals(1, captor.getValue().getVisibleToBusiness());
        assertTrue(captor.getValue().getPayloadJson().contains("memory.search"));
    }

    @Test
    void retriesWhenSequenceCollides() {
        AgentRunStreamEventMapper mapper = mock(AgentRunStreamEventMapper.class);
        when(mapper.selectMaxSequenceNo(1L, 7L)).thenReturn(2L, 3L);
        ArgumentCaptor<AgentRunStreamEvent> captor = ArgumentCaptor.forClass(AgentRunStreamEvent.class);
        when(mapper.insert(captor.capture()))
                .thenThrow(new DuplicateKeyException("duplicate sequence"))
                .thenReturn(1);
        AgentRunStreamEventService service = new AgentRunStreamEventService(mapper, new ObjectMapper());

        AgentRunStreamEvent event = service.record(1L, 7L, AgentRunStreamEventType.TOOL_STARTED,
                Map.of("toolName", "memory.search"), true);

        assertEquals(4L, event.getSequenceNo());
        assertEquals(List.of(3L, 4L), captor.getAllValues().stream().map(AgentRunStreamEvent::getSequenceNo).toList());
    }

    @Test
    void listsVisibleEventsWithParsedPayload() {
        AgentRunStreamEventMapper mapper = mock(AgentRunStreamEventMapper.class);
        AgentRunStreamEvent stored = new AgentRunStreamEvent();
        stored.setId(5L);
        stored.setRunId(7L);
        stored.setEventType("TOOL_STARTED");
        stored.setSequenceNo(4L);
        stored.setPayloadJson("{\"toolName\":\"memory.search\",\"status\":\"RUNNING\"}");
        stored.setVisibleToBusiness(1);
        stored.setCreateTime(LocalDateTime.now());
        when(mapper.selectList(any())).thenReturn(List.of(stored));
        AgentRunStreamEventService service = new AgentRunStreamEventService(mapper, new ObjectMapper());

        List<AgentRunStreamEventService.RunStreamEventView> events = service.listForRun(1L, 7L, 3L, 50, false);

        assertEquals(1, events.size());
        assertEquals(4L, events.get(0).sequenceNo());
        assertEquals("memory.search", events.get(0).payload().get("toolName"));
        assertTrue(events.get(0).visibleToBusiness());
    }
}
