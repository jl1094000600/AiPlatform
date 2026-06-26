package com.aipal.service;

import com.aipal.entity.AgentTask;
import com.aipal.security.TenantTaskRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/** Small, bounded P0 dispatcher. It consumes at most one root RUN task per tenant on each tick. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aipal.agent-runtime.dispatcher.enabled", havingValue = "true")
public class AgentRuntimeDispatcher {
    private final TenantTaskRunner tenantTaskRunner;
    private final AgentTaskService taskService;
    private final AgentRunExecutionService executionService;
    @Qualifier("agentRuntimeTaskExecutor")
    private final ThreadPoolTaskExecutor executor;
    @Value("${aipal.agent-runtime.lease-seconds:60}")
    private int leaseSeconds;
    @Value("${aipal.agent-runtime.max-running-per-tenant:2}")
    private int maxRunningPerTenant;
    private final String workerId = "agent-runtime-" + ManagementFactory.getRuntimeMXBean().getName();

    @Scheduled(fixedDelayString = "${aipal.agent-runtime.dispatcher-delay-ms:2000}",
            initialDelayString = "${aipal.agent-runtime.dispatcher-initial-delay-ms:15000}")
    public void dispatch() {
        tenantTaskRunner.forEachActiveTenant("agent-runtime-dispatch", tenant -> {
            if (!hasCapacity()) return;
            if (taskService.runningRootTaskCount() >= maxRunningPerTenant) return;
            AgentTask task = taskService.claimNext(workerId, leaseSeconds);
            if (task != null) submit(task);
        });
    }

    private void submit(AgentTask task) {
        try {
            executor.execute(() -> executionService.execute(task, workerId));
        } catch (RejectedExecutionException rejected) {
            taskService.releaseClaim(task.getId(), workerId, "Dispatcher queue is full");
        }
    }

    private boolean hasCapacity() {
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        return pool.getActiveCount() < pool.getMaximumPoolSize() || pool.getQueue().remainingCapacity() > 0;
    }
}
