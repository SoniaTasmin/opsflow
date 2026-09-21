/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.batch;

import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.domain.ImportJobRepository;
import com.opsflow.platform.correlation.CorrelationId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Rebuilds what a running import needs from its own row. The job parameters carry identifiers
 * only: everything a chunk acts on is read from the database, never from a serialized principal.
 */
@Component
class ImportContextFactory {

    private final ImportJobRepository jobs;

    ImportContextFactory(ImportJobRepository jobs) {
        this.jobs = jobs;
    }

    ImportContext of(String importJobId, String correlationId) {
        return ImportContext.of(
                jobs.findById(ImportJobId.of(UUID.fromString(importJobId))).orElseThrow(),
                CorrelationId.acceptOrGenerate(correlationId));
    }
}
