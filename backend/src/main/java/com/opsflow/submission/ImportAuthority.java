/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

import com.opsflow.identity.UserId;
import java.util.Objects;

/**
 * Durable authority for work that outlives the request. Not a principal: it carries no roles.
 * Role authorization happened at admission; scope is re-checked here against live data.
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
