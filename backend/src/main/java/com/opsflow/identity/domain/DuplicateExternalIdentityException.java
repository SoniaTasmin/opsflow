/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.domain;

/**
 * Raised when two requests provision the same person at the same moment.
 *
 * <p>Two concurrent first-time logins both find no user and both insert. The unique constraint on
 * {@code (oidc_issuer, oidc_subject)} lets exactly one win; the loser sees this and re-reads the
 * row the winner created. Checking before inserting cannot close that window — only the database
 * can — so this is expected control flow, not a failure.
 */
public class DuplicateExternalIdentityException extends RuntimeException {

    public DuplicateExternalIdentityException(ExternalIdentity externalIdentity, Throwable cause) {
        super("User already exists for issuer=" + externalIdentity.issuer(), cause);
    }
}
