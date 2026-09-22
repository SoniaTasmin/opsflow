/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowSelfApproval false everywhere it matters; the switch exists only for a
 *     single-person demo, where segregation of duties makes the workflow impossible to show
 */
@ConfigurationProperties(prefix = "opsflow.workflow")
public record WorkflowProperties(boolean allowSelfApproval) {}
