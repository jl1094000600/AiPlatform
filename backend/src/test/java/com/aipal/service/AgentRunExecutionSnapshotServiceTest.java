package com.aipal.service;

import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentRunExecutionSnapshot;
import com.aipal.entity.AiAgent;
import com.aipal.entity.AiAgentRuntimeConfig;
import com.aipal.entity.AiAgentVersion;
import com.aipal.mapper.AgentRunExecutionSnapshotMapper;
import com.aipal.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunExecutionSnapshotServiceTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createsEncryptedSnapshotAndDecryptsOnlyWithOriginalRunBinding() {
        useTenant();
        AgentRunExecutionSnapshotMapper mapper = mock(AgentRunExecutionSnapshotMapper.class);
        AgentRunExecutionSnapshotService service = service(mapper);
        AgentRun run = run();
        AiAgentRuntimeConfig runtime = runtime();
        ArgumentCaptor<AgentRunExecutionSnapshot> captor = ArgumentCaptor.forClass(AgentRunExecutionSnapshot.class);
        when(mapper.insert(captor.capture())).thenReturn(1);

        service.create(run, agent(), version(), runtime);
        AgentRunExecutionSnapshot stored = captor.getValue();

        assertEquals(1L, stored.getTenantId());
        assertEquals(99L, stored.getRunId());
        assertEquals(AgentRunExecutionSnapshotService.FORMAT, stored.getSnapshotFormat());
        assertNotNull(stored.getIvB64());
        assertNotNull(stored.getCiphertextB64());
        assertNotNull(stored.getPlaintextHash());
        assertFalse(stored.getCiphertextB64().contains("企业私有系统提示词"));
        assertFalse(stored.getCiphertextB64().contains("按需求生成交付物"));

        when(mapper.selectOne(any())).thenReturn(stored);
        Map<String, Object> decrypted = service.decrypt(run);
        Map<?, ?> runtimeConfig = (Map<?, ?>) decrypted.get("runtimeConfig");

        assertEquals(1001, runtimeConfig.get("modelId"));
        assertEquals("企业私有系统提示词", runtimeConfig.get("systemPrompt"));
        assertEquals("按需求生成交付物", runtimeConfig.get("userPromptTemplate"));

        AgentRun tamperedBinding = run();
        tamperedBinding.setDefinitionHash("other-definition-hash");
        assertThrows(IllegalStateException.class, () -> service.decrypt(tamperedBinding));
    }

    @Test
    void rejectsHashMismatchAfterSuccessfulDecrypt() {
        useTenant();
        AgentRunExecutionSnapshotMapper mapper = mock(AgentRunExecutionSnapshotMapper.class);
        AgentRunExecutionSnapshotService service = service(mapper);
        ArgumentCaptor<AgentRunExecutionSnapshot> captor = ArgumentCaptor.forClass(AgentRunExecutionSnapshot.class);
        when(mapper.insert(captor.capture())).thenReturn(1);
        AgentRun run = run();
        service.create(run, agent(), version(), runtime());
        AgentRunExecutionSnapshot stored = captor.getValue();
        stored.setPlaintextHash("bad-hash");
        when(mapper.selectOne(any())).thenReturn(stored);

        assertThrows(IllegalStateException.class, () -> service.decrypt(run));
    }

    private AgentRunExecutionSnapshotService service(AgentRunExecutionSnapshotMapper mapper) {
        AgentRunSnapshotCrypto crypto = new AgentRunSnapshotCrypto("runtime-v1",
                Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        return new AgentRunExecutionSnapshotService(mapper, crypto, new ObjectMapper());
    }

    private void useTenant() {
        TenantContext.set(new TenantContext.Context(7L, "tester", 1L, "tenant-1", false, Set.of(), Set.of()));
    }

    private AgentRun run() {
        AgentRun run = new AgentRun();
        run.setId(99L);
        run.setTenantId(1L);
        run.setProjectKey("REQ-DELIVERY");
        run.setIdempotencyKey("idem-123");
        run.setAgentVersionId(31L);
        run.setDefinitionHash("definition-hash");
        return run;
    }

    private AiAgent agent() {
        AiAgent agent = new AiAgent();
        agent.setId(11L);
        agent.setAgentCode("requirement-delivery");
        return agent;
    }

    private AiAgentVersion version() {
        AiAgentVersion version = new AiAgentVersion();
        version.setId(31L);
        version.setVersion("v1");
        version.setConfig("{\"loop\":\"p0\"}");
        return version;
    }

    private AiAgentRuntimeConfig runtime() {
        AiAgentRuntimeConfig runtime = new AiAgentRuntimeConfig();
        runtime.setModelId(1001L);
        runtime.setDatasetId(2002L);
        runtime.setTopK(6);
        runtime.setTemperature(0.2);
        runtime.setPromptId(3003L);
        runtime.setPromptVersionId(4004L);
        runtime.setSystemPrompt("企业私有系统提示词");
        runtime.setUserPromptTemplate("按需求生成交付物");
        return runtime;
    }
}
