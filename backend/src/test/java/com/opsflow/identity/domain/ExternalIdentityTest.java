/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExternalIdentityTest {

    @Test
    void acceptsANonUuidSubject() {
        // OIDC permits any string subject. Assuming Keycloak's happens to be a UUID is
        // exactly the coupling the application-owned UserId exists to avoid.
        ExternalIdentity identity = new ExternalIdentity("https://issuer.example/realms/opsflow", "user|12345@corp");

        assertThat(identity.subject()).isEqualTo("user|12345@corp");
    }

    @Test
    void theSameSubjectFromDifferentIssuersIsADifferentPerson() {
        // Why the issuer is part of the key rather than assumed: federating a second
        // provider later must not collide with subjects from the first.
        assertThat(new ExternalIdentity("https://issuer-a", "shared-subject"))
                .isNotEqualTo(new ExternalIdentity("https://issuer-b", "shared-subject"));
    }

    @ParameterizedTest
    @CsvSource({"'', subject", "'   ', subject", "issuer, ''", "issuer, '   '"})
    void rejectsABlankIssuerOrSubject(String issuer, String subject) {
        assertThatThrownBy(() -> new ExternalIdentity(issuer, subject)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsValuesLongerThanTheColumnAllows() {
        String tooLong = "x".repeat(256);

        assertThatThrownBy(() -> new ExternalIdentity("issuer", tooLong)).isInstanceOf(IllegalArgumentException.class);
    }
}
