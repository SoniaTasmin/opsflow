/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class OrganizationCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"AB", "BERLIN_OPS", "EU-WEST-1", "X9"})
    void acceptsAnUnambiguousCode(String code) {
        assertThat(new OrganizationCode(code).value()).isEqualTo(code);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {"a", "berlin_ops", " BERLIN ", "BERLIN OPS", "BERLIN.OPS", "TOO_LONG_ORGANIZATION_CODE_VALUE_X"})
    void rejectsAnythingAmbiguousOrOversized(String code) {
        assertThatThrownBy(() -> new OrganizationCode(code)).isInstanceOf(IllegalArgumentException.class);
    }
}
