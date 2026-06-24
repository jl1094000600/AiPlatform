package com.aipal.service;

import com.aipal.entity.AgentTask;
import com.aipal.security.TenantTaskRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;

/** Small, bounded P0 dispatcher. It consumes at most one root RUN task per tenant on each tick. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aipal.agent-runtime.dispatcher.enabled", havingValue = "true", matchIfMissing = true)
public class AgentRuntimeDispatcher {
    private final TenantTaskRunner tenantTaskRunner;
    private final AgentTaskService taskService;
    private final AgentRunExecutionService executionService;
    private final String workerId = "agent-runtime-" + ManagementFactory.getRuntimeMXBean().getName();

    @Scheduled(fixedDelayString = "${aipal.agent-runtime.dispatcher-delay-ms:2000}",
            initialDelayString = "${aipal.agent-runtime.dispatcher-initial-delay-ms:15000}")
    public void dispatch() {
        tenantTaskRunner.forEachActiveTenant("agent-runtime-dispatch", tenant -> {
            AgentTask task = taskService.claimNext(workerId, 60);
            if (task != null) executionService.execute(task, workerId);
        });
    }
}
