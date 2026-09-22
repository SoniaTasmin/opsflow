/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

/**
 * The basis one import was admitted on — not a persisted role. Written once from a verified token
 * and read only when that job applies values; nothing else may read it as "this user is admin".
 */
public enum ImportAuthorizationMode {

    /** Membership is re-checked per group while the job runs. */
    ORGANIZATION_MEMBER,

    /** Waives membership and nothing else. */
    ADMIN_OVERRIDE
}
