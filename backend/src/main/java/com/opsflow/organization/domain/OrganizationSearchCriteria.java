/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.domain;

import com.opsflow.organization.OrganizationId;
import java.util.Set;

/** {@code visibleTo} is null for a caller who may see every organization. */
public record OrganizationSearchCriteria(
        String nameOrCodeContains, OrganizationStatus status, Set<OrganizationId> visibleTo, int page, int size) {

    public boolean isUnrestricted() {
        return visibleTo == null;
    }
}
