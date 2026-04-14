package com.aipal.common;

import com.alibaba.ttl.TtlRunnable;
import java.util.UUID;

public class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new InheritableThreadLocal<>();

    public static String getTraceId() {
        String traceId = TRACE_ID.get();
        return traceId != null ? traceId : generateTraceId();
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static void clear() {
        TRACE_ID.remove();
    }

    public static Runnable wrap(Runnable runnable) {
        return TtlRunnable.get(runnable);
    }
}
