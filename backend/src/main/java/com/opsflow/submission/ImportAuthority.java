/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

import com.opsflow.identity.UserId;
import java.util.Objects;

/**
 * Durable authority for work that runs after the request has ended.
 *
 * <p>Deliberately not a security principal: it carries no roles, because roles live in a token
 * that a batch thread does not have and are never persisted. Global role authorization happened
 * once, at admission, against a verified token; scope authorization is re-checked here against
 * live data.
 */
public record ImportAuthority(UserId initiatedBy, ImportAuthorizationMode mode) {

    public ImportAuthority {
        Objects.requireNonNull(initiatedBy, "initiatedBy is required");
        Objects.requireNonNull(mode, "mode is required");
    }

    public boolean waivesMembership() {
        return mode == ImportAuthorizationMode.ADMIN_OVERRIDE;
    }
}
