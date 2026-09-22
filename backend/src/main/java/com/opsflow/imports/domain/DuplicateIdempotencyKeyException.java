/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.domain;

/** This request lost the unique index; the winner's job is the answer, so the caller re-reads it. */
public class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(String idempotencyKey) {
        super("Idempotency key already used: " + idempotencyKey);
    }
}
