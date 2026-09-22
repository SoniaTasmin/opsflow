/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.domain;

/**
 * Two concurrent first logins both insert and the unique index picks a winner; the loser re-reads
 * the winner's row. Expected control flow, not a failure.
 */
public class DuplicateExternalIdentityException extends RuntimeException {

    public DuplicateExternalIdentityException(ExternalIdentity externalIdentity, Throwable cause) {
        super("User already exists for issuer=" + externalIdentity.issuer(), cause);
    }
}
