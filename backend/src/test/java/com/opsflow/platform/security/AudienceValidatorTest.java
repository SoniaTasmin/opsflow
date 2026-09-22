/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("opsflow-api");

    private static Jwt tokenFor(List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("subject-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (audience != null) {
            builder.audience(audience);
        }
        return builder.build();
    }

    @Test
    void acceptsATokenIssuedForThisApi() {
        assertThat(validator.validate(tokenFor(List.of("opsflow-api"))).hasErrors())
                .isFalse();
    }

    @Test
    void acceptsATokenListingThisApiAmongSeveralAudiences() {
        assertThat(validator
                        .validate(tokenFor(List.of("other-api", "opsflow-api")))
                        .hasErrors())
                .isFalse();
    }

    @Test
    void rejectsATokenMintedForADifferentClient() {
        // Signature and issuer prove a token is genuine, not that it was meant for us.
        assertThat(validator.validate(tokenFor(List.of("some-other-api"))).hasErrors())
                .isTrue();
    }

    @Test
    void rejectsATokenWithNoAudienceAtAll() {
        assertThat(validator.validate(tokenFor(null)).hasErrors()).isTrue();
    }
}
