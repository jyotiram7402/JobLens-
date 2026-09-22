package com.joblens.api.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request a correlation identifier and publishes it three ways:
 * in the logging {@link MDC} (so the configured log pattern prints it on every
 * line), on the response header (so a client can quote it), and as a request
 * attribute (so the exception handler can put it in the error body).
 *
 * <p>An inbound {@code X-Correlation-Id} is honoured when present, which lets a
 * trace span the frontend and the future AI service without adding a tracing
 * platform. Otherwise one is generated.
 *
 * <p>Runs first in the chain so that nothing downstream logs without an id, and
 * always clears the MDC afterwards -- the thread is pooled and a leaked value
 * would be attributed to an unrelated request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "traceId";
    public static final String REQUEST_ATTRIBUTE = "joblens.traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);

        MDC.put(MDC_KEY, correlationId);
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(HEADER_NAME, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String inbound = request.getHeader(HEADER_NAME);
        if (StringUtils.hasText(inbound)) {
            // Truncated because the value is echoed into logs and responses, and
            // an unbounded client-supplied string should not be either.
            return inbound.length() > 64 ? inbound.substring(0, 64) : inbound;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * The correlation id for the request being handled, or {@code "unknown"}
     * outside a request (scheduled work, startup).
     */
    public static String currentCorrelationId() {
        String value = MDC.get(MDC_KEY);
        return StringUtils.hasText(value) ? value : "unknown";
    }
}
