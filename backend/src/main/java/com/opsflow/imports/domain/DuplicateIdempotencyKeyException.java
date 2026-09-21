/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.domain;

/**
 * Two requests carrying the same idempotency key raced, and this one lost the unique index. The
 * winner's job is the answer; the caller re-reads it rather than reporting a conflict.
 */
public class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(String idempotencyKey) {
        super("Idempotency key already used: " + idempotencyKey);
    }
}
