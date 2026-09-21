/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opsflow.platform.correlation.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies the error contract as a client actually experiences it: status, media type, and the
 * exact JSON shape.
 *
 * <p>Driven through a test-only controller rather than a real endpoint, so the contract is
 * pinned down before any feature depends on it and the test does not break when features change.
 */
class ErrorContractControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // No application context: this verifies exception-to-HTTP mapping, which needs a
        // controller, the advice and the correlation filter, and nothing else. Standalone
        // setup keeps it fast and stops unrelated configuration from breaking it.
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(new ProblemDetailFactory(Clock.systemUTC())))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void notFoundProducesProblemJsonWithAStableCode() throws Exception {
        mockMvc.perform(get("/__test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.type").value("urn:opsflow:problem:resource-not-found"))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.correlationId").value(notNullValue()))
                .andExpect(jsonPath("$.resourceType").value("Submission"));
    }

    @Test
    void businessRuleViolationIs422AndReportsItsSpecificRuleCode() throws Exception {
        mockMvc.perform(get("/__test/business-rule"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REQUIRED_METRICS_MISSING"))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void conflictIs409() throws Exception {
        mockMvc.perform(get("/__test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.expectedVersion").value(7));
    }

    @Test
    void errorContextCannotOverwriteAReservedProblemField() throws Exception {
        // An exception's context map is supplied by whoever threw it. A key like "code"
        // would otherwise overwrite the machine-readable error code with an unrelated
        // value - silently, and only for the exceptions that happen to carry that key.
        mockMvc.perform(get("/__test/hostile-context"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Version conflict"))
                // A non-reserved key is still carried through.
                .andExpect(jsonPath("$.organizationCode").value("BERLIN_OPS"));
    }

    @Test
    void accessDeniedIs403() throws Exception {
        mockMvc.perform(get("/__test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void bodyValidationFailureListsEveryOffendingField() throws Exception {
        mockMvc.perform(post("/__test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "amount")))
                // The generic handler never echoes what was submitted.
                .andExpect(jsonPath("$.errors[*].rejectedValue").doesNotExist());
    }

    @Test
    void aRejectedStringValueIsNotReflectedIntoTheResponse() throws Exception {
        // The generic validation handler sees every request body in the application,
        // including ones carrying credentials or personal data, and cannot tell which
        // field is which. Reflecting a submitted value would put it in the response body
        // and in anything that records responses.
        String secretish = "super-secret-value-that-must-not-come-back";

        String body = mockMvc.perform(post("/__test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"amount\":-5,\"token\":\"" + secretish + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].rejectedValue").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain(secretish);
    }

    @Test
    void unparseableBodyIs400AndDoesNotEchoTheBody() throws Exception {
        mockMvc.perform(post("/__test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value(not(containsString("not json"))));
    }

    @Test
    void wrongParameterTypeIs400() throws Exception {
        mockMvc.perform(get("/__test/number/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors[0].code").value("TYPE_MISMATCH"));
    }

    @Test
    void unexpectedFailureIs500AndLeaksNothingInternal() throws Exception {
        mockMvc.perform(get("/__test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.correlationId").value(notNullValue()))
                // No stack trace, no exception class name, no internal message.
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void correlationIdIsEchoedAndReusedWhenTheClientSuppliesOne() throws Exception {
        mockMvc.perform(get("/__test/not-found").header(CorrelationIdFilter.HEADER, "client-correlation-1"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "client-correlation-1"))
                .andExpect(jsonPath("$.correlationId").value("client-correlation-1"));
    }

    @Test
    void aMalformedClientCorrelationIdIsReplacedRatherThanTrusted() throws Exception {
        mockMvc.perform(get("/__test/not-found").header(CorrelationIdFilter.HEADER, "bad id with spaces"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, not("bad id with spaces")))
                .andExpect(jsonPath("$.correlationId").value(notNullValue()));
    }

    @RestController
    static class TestController {

        record Payload(@NotBlank String name, @Min(0) int amount) {}

        @GetMapping("/__test/not-found")
        void notFound() {
            throw NotFoundException.of("Submission", "abc-123");
        }

        @GetMapping("/__test/business-rule")
        void businessRule() {
            throw new BusinessRuleException("REQUIRED_METRICS_MISSING", "2 required metrics have no value");
        }

        @GetMapping("/__test/conflict")
        void conflict() {
            throw new ConflictException(
                    ErrorCode.VERSION_CONFLICT, "Submission was modified concurrently", Map.of("expectedVersion", 7));
        }

        @GetMapping("/__test/hostile-context")
        void hostileContext() {
            throw new ConflictException(
                    ErrorCode.VERSION_CONFLICT,
                    "Concurrent modification",
                    Map.of(
                            "code", "SHOULD_NOT_WIN",
                            "status", 999,
                            "title", "should not win",
                            "organizationCode", "BERLIN_OPS"));
        }

        @GetMapping("/__test/denied")
        void denied() {
            throw new AccessDeniedDomainException("Not permitted to approve this submission");
        }

        @PostMapping("/__test/validate")
        void validate(@Valid @RequestBody Payload payload) {}

        @GetMapping("/__test/number/{value}")
        void number(@PathVariable int value) {}

        @GetMapping("/__test/boom")
        void boom() {
            throw new IllegalStateException("internal detail that must not reach the client");
        }
    }
}
