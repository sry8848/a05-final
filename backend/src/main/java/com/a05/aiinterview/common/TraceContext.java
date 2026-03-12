package com.a05.aiinterview.common;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 请求级 trace 上下文工具。
 */
public final class TraceContext {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String REQUEST_ID_KEY = "requestId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private TraceContext() {
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static String getRequestId() {
        return MDC.get(REQUEST_ID_KEY);
    }

    public static String getOrCreateTraceId() {
        String traceId = getTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    public static String getOrCreateRequestId() {
        String requestId = getRequestId();
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
            MDC.put(REQUEST_ID_KEY, requestId);
        }
        return requestId;
    }
}
