/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.application;

import com.opsflow.imports.domain.ImportJobView;

/** @param created false when an idempotency key returned the import that already existed */
public record UploadOutcome(ImportJobView job, boolean created) {}
