package com.clinic.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID = "requestId";
    private static final String TRACE_ID = "traceId";
    private final Counter requestCounter;

    public RequestContextLoggingFilter(MeterRegistry meterRegistry) {
        this.requestCounter = Counter.builder("http.server.requests.total")
                .description("Total incoming requests")
                .register(meterRegistry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = java.util.UUID.randomUUID().toString();
        }
        MDC.put(REQUEST_ID, requestId);
        String traceId = MDC.get(TRACE_ID);
        if (traceId == null) {
            MDC.put(TRACE_ID, requestId);
        }
        requestCounter.increment();
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID);
            if (traceId == null) {
                MDC.remove(TRACE_ID);
            }
        }
    }
}