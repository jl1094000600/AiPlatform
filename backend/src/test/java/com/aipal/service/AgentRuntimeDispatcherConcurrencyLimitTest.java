package com.aipal.service;

import com.aipal.entity.AgentTask;
import com.aipal.entity.SysTenant;
import com.aipal.security.TenantTaskRunner;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Field;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W5: execution concurrency limit rejection.
 * Dispatcher must not claim new root RUN tasks when the per-tenant limit is reached
 * or the executor pool has no capacity, and a rejected submit must release the claim.
 */
class AgentRuntimeDispatcherConcurrencyLimitTest {

    @Test
    void doesNotClaimWhenPerTenantRunningLimitIsReached() {
        AgentTaskService taskService = mock(AgentTaskService.class);
        when(taskService.runningRootTaskCount()).thenReturn(2L);
        AgentRuntimeDispatcher dispatcher = dispatcher(taskService, mock(AgentRunExecutionService.class),
                executorWithPool(2, 100), 2);

        dispatcher.dispatch();

        verify(taskService, never()).claimNext(anyString(), anyInt());
    }

    @Test
    void claimsWhenUnderPerTenantRunningLimit() {
        AgentTaskService taskService = mock(AgentTaskService.class);
        when(taskService.runningRootTaskCount()).thenReturn(1L);
        AgentTask task = task(11L);
        when(taskService.claimNext(anyString(), anyInt())).thenReturn(task);
        AgentRunExecutionService executionService = mock(AgentRunExecutionService.class);
        ThreadPoolTaskExecutor executor = executorWithPool(2, 100);
        AgentRuntimeDispatcher dispatcher = dispatcher(taskService, executionService, executor, 2);

        dispatcher.dispatch();

        verify(taskService).claimNext(anyString(), anyInt());
    }

    @Test
    void doesNotClaimWhenExecutorPoolHasNoCapacity() {
        AgentTaskService taskService = mock(AgentTaskService.class);
        ThreadPoolExecutor saturated = mock(ThreadPoolExecutor.class);
        when(saturated.getActiveCount()).thenReturn(4);
        when(saturated.getMaximumPoolSize()).thenReturn(4);
        @SuppressWarnings("unchecked")
        java.util.concurrent.BlockingQueue<Runnable> emptyQueue = mock(java.util.concurrent.BlockingQueue.class);
        when(emptyQueue.remainingCapacity()).thenReturn(0);
        when(saturated.getQueue()).thenReturn(emptyQueue);

        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        when(executor.getThreadPoolExecutor()).thenReturn(saturated);

        AgentRuntimeDispatcher dispatcher = dispatcher(taskService, mock(AgentRunExecutionService.class),
                executor, 10);

        dispatcher.dispatch();

        verify(taskService, never()).claimNext(anyString(), anyInt());
    }

    @Test
    void rejectedSubmitReleasesClaimWithQueueFullReason() {
        AgentTaskService taskService = mock(AgentTaskService.class);
        when(taskService.runningRootTaskCount()).thenReturn(0L);
        AgentTask task = task(21L);
        when(taskService.claimNext(anyString(), anyInt())).thenReturn(task);

        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        ThreadPoolExecutor pool = mock(ThreadPoolExecutor.class);
        when(pool.getActiveCount()).thenReturn(0);
        when(pool.getMaximumPoolSize()).thenReturn(2);
        @SuppressWarnings("unchecked")
        java.util.concurrent.BlockingQueue<Runnable> queue = mock(java.util.concurrent.BlockingQueue.class);
        when(queue.remainingCapacity()).thenReturn(5);
        when(pool.getQueue()).thenReturn(queue);
        when(executor.getThreadPoolExecutor()).thenReturn(pool);
        doAnswer(invocation -> {
            throw new RejectedExecutionException("queue full");
        }).when(executor).execute(any(Runnable.class));

        AgentRuntimeDispatcher dispatcher = dispatcher(taskService, mock(AgentRunExecutionService.class),
                executor, 2);

        dispatcher.dispatch();

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(taskService).releaseClaim(org.mockito.ArgumentMatchers.eq(21L), anyString(), reason.capture());
        assertEquals("Dispatcher queue is full", reason.getValue());
    }

    @Test
    void successfulSubmitDoesNotReleaseClaim() {
        AgentTaskService taskService = mock(AgentTaskService.class);
        when(taskService.runningRootTaskCount()).thenReturn(0L);
        AgentTask task = task(31L);
        when(taskService.claimNext(anyString(), anyInt())).thenReturn(task);

        AtomicInteger executed = new AtomicInteger();
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        ThreadPoolExecutor pool = mock(ThreadPoolExecutor.class);
        when(pool.getActiveCount()).thenReturn(0);
        when(pool.getMaximumPoolSize()).thenReturn(2);
        @SuppressWarnings("unchecked")
        java.util.concurrent.BlockingQueue<Runnable> queue = mock(java.util.concurrent.BlockingQueue.class);
        when(queue.remainingCapacity()).thenReturn(5);
        when(pool.getQueue()).thenReturn(queue);
        when(executor.getThreadPoolExecutor()).thenReturn(pool);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            executed.incrementAndGet();
            return null;
        }).when(executor).execute(any(Runnable.class));

        AgentRunExecutionService executionService = mock(AgentRunExecutionService.class);
        AgentRuntimeDispatcher dispatcher = dispatcher(taskService, executionService, executor, 2);

        dispatcher.dispatch();

        assertEquals(1, executed.get());
        verify(executionService).execute(any(AgentTask.class), anyString());
        verify(taskService, never()).releaseClaim(any(), any(), any());
    }

    private static AgentTask task(Long id) {
        AgentTask task = new AgentTask();
        task.setId(id);
        task.setTenantId(1L);
        task.setRunId(99L);
        task.setTaskType("RUN");
        task.setStatus("QUEUED");
        return task;
    }

    private static ThreadPoolTaskExecutor executorWithPool(int maxSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.initialize();
        return executor;
    }

    @SuppressWarnings("unchecked")
    private static AgentRuntimeDispatcher dispatcher(AgentTaskService taskService,
                                                     AgentRunExecutionService executionService,
                                                     ThreadPoolTaskExecutor executor,
                                                     int maxRunningPerTenant) {
        TenantTaskRunner tenantTaskRunner = mock(TenantTaskRunner.class);
        doAnswer(invocation -> {
            Consumer<SysTenant> consumer = invocation.getArgument(1);
            SysTenant tenant = new SysTenant();
            tenant.setId(1L);
            tenant.setTenantCode("t1");
            tenant.setStatus(1);
            consumer.accept(tenant);
            return null;
        }).when(tenantTaskRunner).forEachActiveTenant(anyString(), any(Consumer.class));

        AgentRuntimeDispatcher dispatcher = new AgentRuntimeDispatcher(
                tenantTaskRunner, taskService, executionService, executor);
        setField(dispatcher, "maxRunningPerTenant", maxRunningPerTenant);
        setField(dispatcher, "leaseSeconds", 60);
        return dispatcher;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to set " + name, e);
        }
    }
}
