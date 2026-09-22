/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

/** The organization, period or submission state invalidated every row before any was considered. */
public record GroupRejection(String code, String message) {}
