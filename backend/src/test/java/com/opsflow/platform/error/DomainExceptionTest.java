/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    void notFoundCarriesResourceTypeAndId() {
        NotFoundException ex = NotFoundException.of("Submission", "abc-123");

        assertThat(ex.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(ex.context()).containsEntry("resourceType", "Submission").containsEntry("id", "abc-123");
    }

    @Test
    void businessRuleExceptionReports422AndItsOwnRuleCode() {
        BusinessRuleException ex = new BusinessRuleException("REQUIRED_METRICS_MISSING", "2 required metrics missing");

        assertThat(ex.errorCode().status()).isEqualTo(422);
        assertThat(ex.ruleCode()).isEqualTo("REQUIRED_METRICS_MISSING");
    }

    @Test
    void conflictExceptionRejectsANonConflictErrorCode() {
        // Guards the 409/422 boundary at construction time rather than letting a
        // mis-categorised error reach a client.
        assertThatThrownBy(() -> new ConflictException(ErrorCode.RESOURCE_NOT_FOUND, "wrong"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a 409");
    }

    @Test
    void contextIsDefensivelyCopiedAndUnmodifiable() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("id", "1");
        NotFoundException ex = new NotFoundException("gone", mutable);

        mutable.put("injected", "later");

        assertThat(ex.context()).containsOnlyKeys("id");
        assertThatThrownBy(() -> ex.context().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }
}
