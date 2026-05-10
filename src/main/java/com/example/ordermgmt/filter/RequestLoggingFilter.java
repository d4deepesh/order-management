package com.example.ordermgmt.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * REQUEST LOGGING FILTER
 *
 * CONCEPT: Servlet Filter -- runs at Tomcat level, BEFORE DispatcherServlet
 *
 * ✅ Main purpose of this filter:
 *
 * | Purpose         | What it does                        |
 * | --------------- | ----------------------------------- |
 * | Request logging | logs incoming/outgoing requests     |
 * | Correlation ID  | tracks request across logs/services |
 * | Timing          | measures request duration           |
 * | MDC setup       | adds request ID to all logs         |
 *
 *
 * INTERVIEW POINTS:
 *
 * OncePerRequestFilter (extends this instead of Filter directly)
 * - Guarantees doFilterInternal() runs EXACTLY ONCE per request
 * - Even in forward/include dispatch scenarios
 * - Always extend OncePerRequestFilter for Spring-managed filters
 *
 * Filter vs HandlerInterceptor:
 * ┌──────────────────┬──────────────────────────────────┐
 * │ Filter           │ Servlet level, before Dispatcher │
 * │                  │ no Spring context access         │
 * │                  │ applies to ALL requests          │
 * ├──────────────────┼──────────────────────────────────┤
 * │ Interceptor      │ Spring MVC level,after Dispatcher│
 * │                  │ full Spring context access       │
 * │                  │ applies to Spring MVC only       │
 * └──────────────────┴──────────────────────────────────┘
 *
 * Use Filter for:
 *  - Request/response logging (needs full round-trip timing)
 *  - CORS headers (before Spring Security reads request)
 *  - Character encoding
 *  - Correlation ID / MDC setup
 *
 * MDC (Mapped Diagnostic Context):
 *  - ThreadLocal map for log correlation
 *  - Set correlationId here, appears in ALL logs for this request
 *  - MUST be cleared in finally block (thread pool reuse) -- MDC.clear();
 *
 *  @Order(1) -- this filter runs first in the chain
 *  Important because: MDC/correlation ID should exist before other logs
 */
@Slf4j
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER =
            "X-Correlation-Id";
    private static final String CORRELATION_ID_MDC_KEY =
            "correlationId";
    private static final String REQUEST_ID_ATTR =
            "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        // generate or extract correlation ID
        // CLIENT may send one for end-to-end tracing
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // store in MDC -- available in ALL log statements
        // for this request thread
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

        // store in request attribute -- accessible in interceptors
        request.setAttribute(REQUEST_ID_ATTR, correlationId);

        // echo correlation ID back in response header
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        // PRE-PROCESSING -- before DispatcherServlet and controller
        log.info(">>> INCOMING REQUEST | correlationId={} | " +
                        "method={} | uri={} | remoteAddr={}",
                correlationId,
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr());

        try {
            // pass to next filter or DispatcherServlet
            // Without this: ❌ request stops
            filterChain.doFilter(request, response);

        } finally {
            // POST-PROCESSING -- after response is written
            // finally block ensures this ALWAYS runs even on exception
            long duration = System.currentTimeMillis() - startTime;

            log.info("<<< OUTGOING RESPONSE | correlationId={} | " +
                            "status={} | duration={}ms",
                    correlationId,
                    response.getStatus(),
                    duration);

            // CRITICAL: always clear MDC in finally block
            // Threads are reused from pool -- stale MDC causes
            // log entries from previous requests to appear
            MDC.clear();
        }
    }
}


