package com.aipal.agent;

import com.aipal.entity.SysTenant;
import com.aipal.security.TenantContext;
import com.aipal.security.TenantTaskRunner;
import com.aipal.service.A2AMessageService;
import com.aipal.service.AgentRegistry;
import com.aipal.service.HeartbeatService;
import com.aipal.service.TtsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TtsAgentTest {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void registersHandlerInsideEachTenantContext() throws Exception {
        A2AMessageService messageService = mock(A2AMessageService.class);
        TenantTaskRunner tenantTaskRunner = mock(TenantTaskRunner.class);
        SysTenant tenant = new SysTenant();
        tenant.setId(7L);
        tenant.setTenantCode("tenant-7");
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<SysTenant> task = invocation.getArgument(1);
            TenantContext.runWithContext(new TenantContext.Context(
                    null, "system:test", 7L, "tenant-7", false, Set.of("system-task"), Set.of()),
                    () -> task.accept(tenant));
            return null;
        }).when(tenantTaskRunner).forEachActiveTenant(eq("tts-agent-registration"), any());

        TtsAgent agent = new TtsAgent(
                mock(TtsService.class), mock(AgentRegistry.class), messageService,
                mock(HeartbeatService.class), tenantTaskRunner);
        ReflectionTestUtils.setField(agent, "agentCode", "tts-agent");
        ReflectionTestUtils.setField(agent, "enabled", true);

        agent.run(new DefaultApplicationArguments(new String[0]));

        verify(messageService).registerHandler(eq("tts-agent"), any());
    }
}
