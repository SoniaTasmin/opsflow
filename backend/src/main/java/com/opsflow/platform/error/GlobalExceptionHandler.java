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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * A 500 carries a generic message and the correlation id — never a stack trace, exception name,
 * SQL or constraint name. 4xx logs at WARN without a stack trace so a bad client cannot page
 * anyone.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        log.warn("Business rule violated: rule={} detail={}", ex.ruleCode(), ex.getMessage());
        return problems.create(ex.errorCode(), ex.ruleCode(), ex.getMessage(), ex.context(), List.of());
    }

    @ExceptionHandler(MalformedRequestException.class)
    public ProblemDetail handleMalformedRequest(MalformedRequestException ex) {
        log.warn("Malformed request: code={} detail={}", ex.code(), ex.getMessage());
        return problems.create(ex.errorCode(), ex.code(), ex.getMessage(), ex.context(), List.of());
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        log.warn("{}: {}", ex.errorCode(), ex.getMessage());
        return problems.create(ex.errorCode(), ex.getMessage(), ex.context());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex) {
        // rejectedValue stays null: this handler sees every request body, including ones
        // carrying credentials, and cannot tell which field is which.
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
        // As above: the submitted value is never echoed back.
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        // The parser message can quote the request body, so it is not echoed.
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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Rejected an upload above the configured limit: maxBytes={}", ex.getMaxUploadSize());
        return problems.create(ErrorCode.PAYLOAD_TOO_LARGE, "The uploaded file is larger than this API accepts");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return problems.create(ErrorCode.RESOURCE_NOT_FOUND, "No endpoint matches this request");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        // The caller is authenticated, so 403 is right here. A denial that must not confirm the
        // resource exists is thrown as NotFoundException instead.
        log.info("Denied an authenticated request: {}", ex.getMessage());
        return problems.create(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // Full detail to the log; only the correlation id to the client.
        log.error("Unhandled exception", ex);
        return problems.create(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
    }
}
