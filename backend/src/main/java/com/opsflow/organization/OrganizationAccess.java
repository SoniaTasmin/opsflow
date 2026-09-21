/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization;

import com.opsflow.identity.UserId;
import java.util.Set;

public interface OrganizationAccess {

    boolean isMember(UserId userId, OrganizationId organizationId);

    Set<OrganizationId> organizationIdsOf(UserId userId);
}
