/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.application;

import com.opsflow.identity.Actor;
import com.opsflow.identity.CurrentActorProvider;
import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.ImportStatus;
import com.opsflow.imports.domain.ImportJobRepository;
import com.opsflow.imports.domain.ImportJobView;
import com.opsflow.imports.domain.ImportRowError;
import com.opsflow.imports.domain.ImportSearchCriteria;
import com.opsflow.organization.OrganizationAccess;
import com.opsflow.platform.error.NotFoundException;
import com.opsflow.reporting.PeriodId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportQueryService {

    private static final Logger log = LoggerFactory.getLogger(ImportQueryService.class);

    private final ImportJobRepository jobs;
    private final CurrentActorProvider currentActor;
    private final OrganizationAccess access;
    private final ImportProperties properties;

    ImportQueryService(
            ImportJobRepository jobs,
            CurrentActorProvider currentActor,
            OrganizationAccess access,
            ImportProperties properties) {
        this.jobs = jobs;
        this.currentActor = currentActor;
        this.access = access;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public ImportJobRepository.ImportPage search(PeriodId reportingPeriodId, ImportStatus status, int page, int size) {
        Actor actor = currentActor.requireActor();
        return jobs.search(new ImportSearchCriteria(
                actor.isAdmin() ? null : access.organizationIdsOf(actor.userId()),
                actor.isAdmin() ? null : actor.userId(),
                reportingPeriodId,
                status,
                page,
                size));
    }

    @Transactional(readOnly = true)
    public ImportJobView require(ImportJobId id) {
        Actor actor = currentActor.requireActor();
        ImportJobView view = jobs.viewById(id).orElseThrow(() -> NotFoundException.of("Import", id));
        if (!visible(actor, view)) {
            log.info("Hiding an import from a caller who cannot see it importJobId={} userId={}", id, actor.userId());
            throw NotFoundException.of("Import", id);
        }
        return view;
    }

    @Transactional(readOnly = true)
    public List<ImportRowError> errorsOf(ImportJobId id) {
        require(id);
        return jobs.errorsOf(id, properties.maxErrors());
    }

    private boolean visible(Actor actor, ImportJobView view) {
        if (actor.isAdmin() || view.initiatedBy().equals(actor.userId())) {
            return true;
        }
        return view.organizationId() != null && access.isMember(actor.userId(), view.organizationId());
    }
}
