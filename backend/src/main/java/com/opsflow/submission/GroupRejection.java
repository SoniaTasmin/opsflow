/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

/**
 * The whole group was refused before any value was considered — the organization, the period or
 * the submission's state made every row in it invalid.
 */
public record GroupRejection(String code, String message) {}
