# ADR-0005: The review workflow lives inside the submission module

- **Status:** Accepted
- **Date:** 2026-09-20

## Context

The initial capability list named "submission approval/rejection workflow" as a separate concern, which suggests a `workflow` module alongside `submission`.

## Decision

The review workflow lives **inside** `submission`. There is no `workflow` module.

`Submission` is one aggregate owning its status, its values, and its transition history. Review logic is visibly separated *within* the module, in `submission.domain.review` and `submission.application.review`.

The **period summary** query also lives here, for the same reason: it is a read model over submission rows.

## Consequences

**Easier:** the system's most important invariant — that status and value-editability stay consistent — is enforced inside a single aggregate in a single transaction. A separate module would own no tables (status lives on the submission row) and would have to reach into `submission` for every operation, which is a package with extra ceremony, not a module.

**Harder:** `submission` is the broadest module, owning the aggregate, the review workflow, the transition history, the import port and the summary query. That is four responsibilities.

**Watch item:** the signal that `submission` has grown too broad is its `application` package needing a third level of sub-packaging, or a change to the summary query forcing a recompile of review code. Neither is true yet.

**What would justify splitting it out:** review acquiring its own persistent state with an independent lifecycle — assignment rules, SLA timers, multi-step approval chains, delegation. At that point it owns tables and becomes a real module.

## Related decision: frozen period scope

A reporting period freezes **two** scopes when it transitions `DRAFT → OPEN`: which metrics are collected, and which organizations participate (`reporting_period_organization`).

Participation must be frozen because otherwise historical statistics are mutable: create an organization in July and last quarter's completion ratio silently drops; archive one and it silently rises. A completion figure that changes retroactively is simply wrong.

The summary reads the frozen participant set through `PeriodQuery.participantsOf(...)` — a **port call, not a join**. The obvious implementation (`LEFT JOIN reporting_period_organization`) would be `submission` issuing SQL against a `reporting`-owned table, violating the second enforcement level in ADR-0001. The set is at most 500 ids; the difference is computed in the application service.

**Accepted consequence:** an organization archived after a period opens stays in that period's denominator and keeps counting as not-started if it never submitted. That is the correct reading of "frozen" — it did participate — and it is what makes history stable. Allowing an administrator to withdraw a participant with a reason is the right long-term answer and is deliberately not in the MVP.

## Alternatives considered

**A separate `workflow` module.** Rejected: it owns no data, so the boundary would cut straight through the invariant that matters most.

**Status in a separate table.** Rejected: it turns every transition into a two-table transactional dance for no benefit.
