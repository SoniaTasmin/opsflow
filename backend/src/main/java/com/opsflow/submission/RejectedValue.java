/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

/** One row the submission module refused, with the rule that refused it. */
public record RejectedValue(int rowNumber, String code, String message) {}
