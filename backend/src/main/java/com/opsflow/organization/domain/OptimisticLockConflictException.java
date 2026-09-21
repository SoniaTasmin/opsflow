/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.domain;

import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import java.util.Map;

public class OptimisticLockConflictException extends ConflictException {

    public OptimisticLockConflictException(Object organizationId, long expectedVersion) {
        super(
                ErrorCode.VERSION_CONFLICT,
                "The organization was modified by someone else; reload it and try again",
                Map.of("organizationId", String.valueOf(organizationId), "expectedVersion", expectedVersion));
    }
}
