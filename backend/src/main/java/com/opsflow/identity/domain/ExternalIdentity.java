/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.domain;

/**
 * The {@code iss}/{@code sub} pair a returning user is recognised by. An attribute of a user,
 * never their primary key; the issuer is part of it so a second provider can be federated
 * without subject collisions.
 */
public record ExternalIdentity(String issuer, String subject) {

    private static final int MAX_LENGTH = 255;

    public ExternalIdentity {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("issuer is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
        if (issuer.length() > MAX_LENGTH || subject.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("issuer and subject must be at most " + MAX_LENGTH + " characters");
        }
    }
}
