/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes a correlation id for every request, before anything else runs.
 *
 * <p>Ordered first so that even a request rejected by authentication is still traceable, and so
 * that the id is present in the MDC for any log line the rest of the chain emits.
 *
 * <p>The id is echoed on the response — including error responses — which is what lets a user
 * quote it to support.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        CorrelationId correlationId = CorrelationId.acceptOrGenerate(request.getHeader(HEADER));
        MDC.put(MdcKeys.CORRELATION_ID, correlationId.value());
        response.setHeader(HEADER, correlationId.value());
        try {
            chain.doFilter(request, response);
        } finally {
            // Threads are pooled and, with virtual threads, plentiful. A leaked MDC entry
            // attributes one request's logs to the next, which is both confusing and a
            // privacy problem.
            MDC.remove(MdcKeys.CORRELATION_ID);
        }
    }
}
