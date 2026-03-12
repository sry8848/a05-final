package com.a05.aiinterview.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 统一注入 traceId / requestId。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = firstNonBlank(
                request.getHeader(TraceContext.TRACE_ID_HEADER),
                request.getHeader("traceId"),
                UUID.randomUUID().toString()
        );
        String requestId = firstNonBlank(
                request.getHeader(TraceContext.REQUEST_ID_HEADER),
                request.getHeader("requestId"),
                UUID.randomUUID().toString()
        );

        MDC.put(TraceContext.TRACE_ID_KEY, traceId);
        MDC.put(TraceContext.REQUEST_ID_KEY, requestId);
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        response.setHeader(TraceContext.REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.TRACE_ID_KEY);
            MDC.remove(TraceContext.REQUEST_ID_KEY);
        }
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }
}
