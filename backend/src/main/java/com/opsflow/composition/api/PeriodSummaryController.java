/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.composition.api;

import com.opsflow.composition.application.PeriodSummaryService;
import com.opsflow.reporting.PeriodId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Spans reporting, organization and submission, so it belongs to the composition layer. */
@RestController
@RequestMapping(path = "/api/v1/periods/{periodId}/summary", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Reporting periods", description = "Reporting periods and their scope")
public class PeriodSummaryController {

    private final PeriodSummaryService summaries;

    public PeriodSummaryController(PeriodSummaryService summaries) {
        this.summaries = summaries;
    }

    @GetMapping
    @Operation(
            operationId = "getPeriodSummary",
            summary = "How far through a reporting period its participants are",
            description = "Counted against the period's frozen participant set. Reviewers and"
                    + " administrators see every participant; everyone else sees their own"
                    + " organizations, denominator included.")
    public PeriodSummaryResponse get(@PathVariable UUID periodId) {
        return PeriodSummaryResponse.from(summaries.of(PeriodId.of(periodId)));
    }
}
