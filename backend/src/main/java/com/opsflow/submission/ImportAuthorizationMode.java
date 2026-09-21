/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

/**
 * The basis on which an asynchronous import was admitted.
 *
 * <p>Not a persisted role. It is written once from a verified token at admission, scoped to a
 * single job, and read only when that job applies values. Nothing else may read it as "this user
 * is an administrator".
 */
public enum ImportAuthorizationMode {

    /** The initiator belonged to the target organization; membership is re-checked per group. */
    ORGANIZATION_MEMBER,

    /** An administrator loading centrally. Waives membership and nothing else. */
    ADMIN_OVERRIDE
}
