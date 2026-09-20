/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Requires that a token was actually issued for this API.
 *
 * <p>Without this check, any validly-signed token from the same realm is accepted — including one
 * a user consented to give a completely different client. Signature and issuer validation alone
 * prove the token is genuine, not that it was meant for us; that is the audience-confusion
 * problem, and it is why the {@code aud} claim exists.
 */
class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String requiredAudience;

    AudienceValidator(String requiredAudience) {
        this.requiredAudience = requiredAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getAudience() != null && token.getAudience().contains(requiredAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "The token does not include the required audience",
                "https://datatracker.ietf.org/doc/html/rfc6750#section-3.1"));
    }
}
