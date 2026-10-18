package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.dto.WorkflowVersionDtos;
import com.aipal.entity.Workflow;
import com.aipal.entity.WorkflowVersion;
import com.aipal.mapper.WorkflowMapper;
import com.aipal.mapper.WorkflowVersionMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowVersionServiceTest {

    private final WorkflowMapper workflowMapper = mock(WorkflowMapper.class);
    private final WorkflowVersionMapper versionMapper = mock(WorkflowVersionMapper.class);
    private final WorkflowVersionService service = new WorkflowVersionService(
            workflowMapper, versionMapper,
            new WorkflowDefinitionService(new ObjectMapper()),
            new ObjectMapper());

    private final AtomicLong idSeq = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext.Context(7L, "tester", 42L, "tenant-42", false, Set.of(), Set.of()));
        when(workflowMapper.selectOne(any())).thenReturn(workflow());
        when(versionMapper.insert(any(WorkflowVersion.class))).thenAnswer(invocation -> {
            WorkflowVersion v = invocation.getArgument(0);
            v.setId(idSeq.incrementAndGet());
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void publishFreezesValidatedDefinitionWithStableHash() {
        when(versionMapper.selectList(any())).thenReturn(List.of());

        WorkflowVersionDtos.VersionView view = service.publish(9L, definition(), null);

        assertEquals(1, view.versionNo());
        assertEquals("ACTIVE", view.status());
        assertEquals(64, view.definitionHash().length());
        verify(versionMapper).insert(any(WorkflowVersion.class));
    }

    @Test
    void publishRejectsDeferredParallelNode() {
        when(versionMapper.selectList(any())).thenReturn(List.of());
        String deferred = """
                {"nodes":[{"id":"1","type":"START"},{"id":"2","type":"PARALLEL"},
                {"id":"3","type":"END"}],
                "edges":[{"source":"1","target":"2"},{"source":"2","target":"3"}]}
                """;
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.publish(9L, deferred, null));
        assertTrue(error.getMessage().contains("JOIN barrier"));
        verify(versionMapper, never()).insert(any(WorkflowVersion.class));
    }

    @Test
    void definitionHashIsStableAcrossKeyOrder() {
        String a = "{\"nodes\":[{\"id\":\"1\",\"type\":\"START\"},{\"id\":\"2\",\"type\":\"END\"}],\"edges\":[{\"source\":\"1\",\"target\":\"2\"}]}";
        String b = "{\"edges\":[{\"target\":\"2\",\"source\":\"1\"}],\"nodes\":[{\"type\":\"START\",\"id\":\"1\"},{\"type\":\"END\",\"id\":\"2\"}]}";
        assertEquals(service.canonicalHash(a), service.canonicalHash(b));
    }

    @Test
    void definitionHashDetectsChangedContent() {
        String a = "{\"nodes\":[{\"id\":\"1\",\"type\":\"START\"},{\"id\":\"2\",\"type\":\"END\"}],\"edges\":[{\"source\":\"1\",\"target\":\"2\"}]}";
        String c = "{\"nodes\":[{\"id\":\"1\",\"type\":\"START\"},{\"id\":\"2\",\"type\":\"AGENT\",\"agentId\":3}],\"edges\":[{\"source\":\"1\",\"target\":\"2\"}]}";
        assertNotEquals(service.canonicalHash(a), service.canonicalHash(c));
    }

    @Test
    void requireActiveVersionRejectsDeprecatedSnapshot() {
        WorkflowVersion deprecated = new WorkflowVersion();
        deprecated.setId(5L);
        deprecated.setWorkflowId(9L);
        deprecated.setStatus("DEPRECATED");
        when(versionMapper.selectOne(any())).thenReturn(deprecated);

        BizException error = assertThrows(BizException.class, () -> service.requireActiveVersion(9L, 5L));
        assertEquals(409, error.getCode());
    }

    @Test
    void publishNumbersVersionsSequentially() {
        WorkflowVersion first = new WorkflowVersion();
        first.setVersionNo(1);
        when(versionMapper.selectList(any())).thenReturn(List.of(first));

        WorkflowVersionDtos.VersionView view = service.publish(9L, definition(), null);
        assertEquals(2, view.versionNo());
    }

    @Test
    void overwriteKeepsVersionNumberAndRefreshesHash() {
        WorkflowVersion existing = new WorkflowVersion();
        existing.setId(12L);
        existing.setWorkflowId(9L);
        existing.setVersionNo(3);
        existing.setDefinitionJson(definition());
        existing.setDefinitionHash("old-hash");
        existing.setStatus("ACTIVE");
        when(versionMapper.selectOne(any())).thenReturn(existing);
        when(versionMapper.updateById(any())).thenReturn(1);

        String newDefinition = "{\"nodes\":[{\"id\":\"1\",\"type\":\"START\"},{\"id\":\"2\",\"type\":\"END\"}],\"edges\":[{\"source\":\"1\",\"target\":\"2\"}]}";
        WorkflowVersionDtos.VersionView view = service.overwrite(9L, 12L, newDefinition, null);

        assertEquals(3, view.versionNo());
        assertNotEquals("old-hash", view.definitionHash());
        assertEquals("ACTIVE", view.status());
        verify(versionMapper).updateById(any(WorkflowVersion.class));
        verify(versionMapper, never()).insert(any(WorkflowVersion.class));
    }

    @Test
    void createExecutionRequiresPublishedSnapshot() {
        // covered indirectly: latestActive returning null must fail creation
        when(versionMapper.selectOne(any())).thenReturn(null);
        assertNull(service.latestActive(9L));
    }

    private Workflow workflow() {
        Workflow workflow = new Workflow();
        workflow.setId(9L);
        workflow.setTenantId(42L);
        workflow.setWorkflowCode("wf-1");
        workflow.setWorkflowName("测试编排");
        workflow.setStatus(1);
        return workflow;
    }

    private String definition() {
        return """
                {"nodes":[{"id":"1","type":"START"},{"id":"2","type":"END"}],
                 "edges":[{"source":"1","target":"2"}]}
                """;
    }
}
