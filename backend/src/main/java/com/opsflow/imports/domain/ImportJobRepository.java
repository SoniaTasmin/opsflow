/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.domain;

import com.opsflow.identity.UserId;
import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.ImportStatus;
import java.util.List;
import java.util.Optional;

public interface ImportJobRepository {

    Optional<ImportJob> findById(ImportJobId id);

    Optional<ImportJobView> viewById(ImportJobId id);

    Optional<ImportJob> findByIdempotencyKey(UserId initiatedBy, String idempotencyKey);

    ImportJob insert(ImportJob job);

    ImportJob save(ImportJob job);

    void recordProgress(ImportJobId id, int processed, int valid, int invalid);

    ImportPage search(ImportSearchCriteria criteria);

    List<ImportJob> findByStatus(ImportStatus status);

    void appendErrors(ImportJobId id, List<ImportRowError> errors);

    List<ImportRowError> errorsOf(ImportJobId id, int limit);

    int errorCount(ImportJobId id);

    record ImportPage(List<ImportJobView> content, long totalElements) {}
}
