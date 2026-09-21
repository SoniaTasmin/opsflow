/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowSelfApproval whether the person who submitted may also approve. False everywhere
 *     it matters; the switch exists because a single-person demo environment is the one place
 *     segregation of duties makes the workflow impossible to show.
 */
@ConfigurationProperties(prefix = "opsflow.workflow")
public record WorkflowProperties(boolean allowSelfApproval) {}
