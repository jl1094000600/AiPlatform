package com.aipal.config;

import com.aipal.common.TraceContext;
import com.aipal.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantContextTaskDecoratorTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        TraceContext.clear();
    }

    @Test
    void propagatesAndClearsTenantAndTraceContext() throws Exception {
        TenantContext.set(new TenantContext.Context(
                7L, "tester", 42L, "tenant-42", false, Set.of(), Set.of()));
        TraceContext.setTraceId("trace-42");

        TenantContextTaskDecorator decorator = new TenantContextTaskDecorator();
        AtomicLong tenantId = new AtomicLong();
        AtomicReference<String> traceId = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> {
            tenantId.set(TenantContext.tenantId());
            traceId.set(TraceContext.getTraceId());
        });
        TenantContext.clear();
        TraceContext.clear();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(decorated).get(5, TimeUnit.SECONDS);
            assertEquals(42L, tenantId.get());
            assertEquals("trace-42", traceId.get());

            AtomicReference<TenantContext.Context> leakedContext = new AtomicReference<>();
            executor.submit(() -> leakedContext.set(TenantContext.get())).get(5, TimeUnit.SECONDS);
            assertNull(leakedContext.get());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void restoresContextAlreadyPresentOnExecutorThread() throws Exception {
        TenantContext.Context workerContext = new TenantContext.Context(
                8L, "worker", 84L, "tenant-84", false, Set.of(), Set.of());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                TenantContext.set(workerContext);
                TraceContext.setTraceId("worker-trace");
            }).get(5, TimeUnit.SECONDS);

            TenantContext.set(new TenantContext.Context(
                    7L, "caller", 42L, "tenant-42", false, Set.of(), Set.of()));
            TraceContext.setTraceId("caller-trace");
            Runnable decorated = new TenantContextTaskDecorator().decorate(() -> {
                assertEquals(42L, TenantContext.tenantId());
                assertEquals("caller-trace", TraceContext.currentTraceId());
            });

            executor.submit(decorated).get(5, TimeUnit.SECONDS);
            executor.submit(() -> {
                assertEquals(workerContext, TenantContext.get());
                assertEquals("worker-trace", TraceContext.currentTraceId());
                TenantContext.clear();
                TraceContext.clear();
            }).get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
