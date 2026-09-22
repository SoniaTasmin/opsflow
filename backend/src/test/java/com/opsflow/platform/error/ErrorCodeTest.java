/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ErrorCodeTest {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void everyCodeMapsToARealHttpErrorStatus(ErrorCode code) {
        assertThat(code.status()).isBetween(400, 599);
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void everyCodeHasATitleAndTypeUri(ErrorCode code) {
        assertThat(code.title()).isNotBlank();
        assertThat(code.typeUri()).startsWith("urn:opsflow:problem:");
    }

    @Test
    void typeUriIsDerivedFromTheConstantName() {
        assertThat(ErrorCode.VERSION_CONFLICT.typeUri()).isEqualTo("urn:opsflow:problem:version-conflict");
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void typeUriIsAUrnRatherThanAnUnservedHttpUrl(ErrorCode code) {
        // RFC 9457 does not require the type to resolve, and an https URI the project does not
        // serve would be a promise it cannot keep.
        assertThat(code.typeUri()).doesNotStartWith("http");
    }

    @Test
    void conflictsAre409AndBusinessRulesAre422() {
        // The distinction the project commits to: 409 means "reload and retry may work",
        // 422 means "this will never work as sent". Locking it down here stops it drifting.
        assertThat(ErrorCode.VERSION_CONFLICT.status()).isEqualTo(409);
        assertThat(ErrorCode.DUPLICATE_RESOURCE.status()).isEqualTo(409);
        assertThat(ErrorCode.BUSINESS_RULE_VIOLATION.status()).isEqualTo(422);
    }
}
