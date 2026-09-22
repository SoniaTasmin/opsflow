/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.batch;

import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.domain.ImportJobRepository;
import com.opsflow.platform.correlation.CorrelationId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Job parameters carry identifiers only; everything else is read from the database. */
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
