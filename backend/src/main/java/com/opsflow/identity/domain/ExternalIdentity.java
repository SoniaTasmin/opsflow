/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.domain;

/**
 * How an identity provider identifies a person: the issuer plus the subject it assigned.
 *
 * <p>The pair is unique and permanent — it is what a returning user is recognised by. The issuer
 * is part of the key rather than assumed, so a second provider can be federated later without
 * subject collisions between them.
 *
 * <p>This is an <em>attribute</em> of a user, never their primary key.
 *
 * @param issuer the {@code iss} claim
 * @param subject the {@code sub} claim
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
