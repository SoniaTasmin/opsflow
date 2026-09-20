/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every exception that escapes a controller into the one error shape defined by
 * {@link ProblemDetailFactory}.
 *
 * <p>Two rules govern what reaches the client:
 *
 * <ul>
 *   <li><b>Nothing internal leaks.</b> A 500 carries a generic message and the correlation id —
 *       never a stack trace, exception class name, SQL statement or constraint name. The detail
 *       goes to the log, where the correlation id connects the two.
 *   <li><b>Client errors are not incidents.</b> 4xx is logged at WARN without a stack trace, so
 *       a misbehaving client cannot fill the error log or page anyone.
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    // ---------------------------------------------------------------- domain

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        log.warn("Business rule violated: rule={} detail={}", ex.ruleCode(), ex.getMessage());
        // The specific rule code replaces the generic enum name: it is what a client switches on.
        return problems.create(ex.errorCode(), ex.ruleCode(), ex.getMessage(), ex.context(), List.of());
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        log.warn("{}: {}", ex.errorCode(), ex.getMessage());
        return problems.create(ex.errorCode(), ex.getMessage(), ex.context());
    }

    // ------------------------------------------------------------ validation

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex) {
        // rejectedValue is deliberately NOT populated here. This handler sees every
        // request body in the application, including ones carrying credentials, tokens
        // or personal data, and it cannot know which field is which. Echoing the
        // submitted value back would reflect that content into a response body and into
        // any log or error tracker that records it. A domain-specific handler may include
        // a value it knows to be a safe scalar; the generic path never does.
        List<ApiFieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ApiFieldError.of(
                        fe.getField(),
                        fe.getCode() == null ? "INVALID" : fe.getCode().toUpperCase(),
                        fe.getDefaultMessage()))
                .toList();
        log.warn("Request body validation failed: {} field error(s)", errors.size());
        return problems.create(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.name(),
                "The request contains invalid fields",
                Map.of(),
                errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        // As above: the invalid value is not reflected back to the client.
        List<ApiFieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> ApiFieldError.of(String.valueOf(v.getPropertyPath()), "INVALID", v.getMessage()))
                .toList();
        log.warn("Constraint violation: {} violation(s)", errors.size());
        return problems.create(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.name(),
                "The request contains invalid values",
                Map.of(),
                errors);
    }

    // --------------------------------------------------------- malformed input

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        // Deliberately does not echo the parser message: it can quote the request body.
        log.warn("Unreadable request body: {}", ex.getMostSpecificCause().getMessage());
        return problems.create(ErrorCode.MALFORMED_REQUEST, "The request body could not be parsed");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parameter type mismatch: name={}", ex.getName());
        return problems.create(
                ErrorCode.MALFORMED_REQUEST,
                ErrorCode.MALFORMED_REQUEST.name(),
                "Parameter '" + ex.getName() + "' has the wrong type",
                Map.of(),
                List.of(ApiFieldError.of(ex.getName(), "TYPE_MISMATCH", "Value is not of the expected type")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName());
        return problems.create(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.name(),
                "A required parameter is missing",
                Map.of(),
                List.of(ApiFieldError.of(ex.getParameterName(), "REQUIRED", "Parameter is required")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return problems.create(ErrorCode.RESOURCE_NOT_FOUND, "No endpoint matches this request");
    }

    // ------------------------------------------------------------- catch-all

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // Full detail to the log; only the correlation id to the client.
        log.error("Unhandled exception", ex);
        return problems.create(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
    }
}
