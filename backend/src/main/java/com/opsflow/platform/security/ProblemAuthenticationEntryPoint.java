/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.security;

import com.opsflow.platform.error.ErrorCode;
import com.opsflow.platform.error.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

/**
 * Returns 401 as a problem detail rather than Spring Security's default empty body.
 *
 * <p>Authentication failures must use the same contract as every other error, so a client's error
 * handling has exactly one shape to deal with — and so the response still carries the correlation
 * id a user would quote to support.
 */
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(ProblemAuthenticationEntryPoint.class);

    private final ProblemDetailFactory problems;
    private final ObjectMapper objectMapper;

    ProblemAuthenticationEntryPoint(ProblemDetailFactory problems, ObjectMapper objectMapper) {
        this.problems = problems;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        // The reason is logged, never returned: telling a caller precisely why a token was
        // rejected helps them craft a better one. The message itself never includes the token.
        log.info("Rejected unauthenticated request: path={} reason={}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problem =
                problems.create(ErrorCode.UNAUTHENTICATED, "Authentication is required to access this resource");
        problem.setInstance(java.net.URI.create(request.getRequestURI()));

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
