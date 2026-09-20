/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CorrelationIdTest {

    @Test
    void generatesAValueThatSatisfiesItsOwnFormat() {
        assertThat(CorrelationId.generate().value()).isNotBlank();
    }

    @Test
    void acceptsAWellFormedClientSuppliedValue() {
        assertThat(CorrelationId.acceptOrGenerate("client-supplied-1234").value())
                .isEqualTo("client-supplied-1234");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "short",
                "has spaces in it",
                "line\nbreak-injection-attempt",
                "semi;colon;separated",
                "this-value-is-far-too-long-to-be-a-reasonable-correlation-identifier-x"
            })
    void replacesAnythingMalformedRatherThanTrustingIt(String candidate) {
        // The value lands in every log line for the request, so a newline or an
        // unbounded string would be a log-injection problem, not a formatting one.
        String result = CorrelationId.acceptOrGenerate(candidate).value();

        assertThat(result).isNotEqualTo(candidate).matches("[A-Za-z0-9_-]{8,64}");
    }

    @Test
    void rejectsAnInvalidValueWhenConstructedDirectly() {
        assertThatThrownBy(() -> new CorrelationId("bad id")).isInstanceOf(IllegalArgumentException.class);
    }
}
