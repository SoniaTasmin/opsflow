# ADR-0006: CSV import writes through the submission aggregate

- **Status:** Accepted
- **Date:** 2026-09-21

## Context

Bulk import is where aggregates usually get bypassed. The tempting shape is an `INSERT ... ON CONFLICT DO UPDATE` straight into `submission_value` from the import module: it is fast, it is a single statement per chunk, and it is wrong. It creates a second writer of submission data with no state machine, no metric validation and no optimistic locking, so a number that no human could have typed can arrive through a file.

A second problem is authority. Import runs after the request that started it has ended. There is no `SecurityContext` on a batch thread, roles are deliberately never persisted (ADR-0003), and calling Keycloak inside a chunk transaction would make the identity provider a hard runtime dependency of every import.

## Decision

**All submission writes go through the `Submission` aggregate, imports included.** The `imports` module depends on one published port, `SubmissionImportService.applyValues(...)`, and on nothing below it. Efficiency comes from *grouping* — a chunk is grouped by organization so one aggregate load serves a whole organization's rows — never from bypassing.

`applyValues` re-checks, in this order: membership (unless waived), organization active, participation in the period's frozen scope, period open, submission editable, metric in the period's scope, value type and range. It returns group rejections in its result so a caller processing many groups can record one error and carry on, and throws on a concurrency conflict, because the right answer to that is a bounded retry of the chunk.

**Authority travels on the job row, not on the thread.** Admission, which happens inside the request against a verified token, records `authorization_mode` — `ORGANIZATION_MEMBER` or `ADMIN_OVERRIDE`. Processing rebuilds an `ImportAuthority` from that row. It carries a `UserId` and the mode, and no roles.

**Idempotency identifies the request, not the file.** `(created_by, Idempotency-Key)` is unique, and the stored fingerprint is `sha256(file checksum | reporting period | scope)`. The same key with the same request returns the existing job; the same key with any difference is a conflict; a new key runs the same file again, which is the retry-after-failure path.

## Consequences

**Easier:** there is exactly one place where a submission value can change, so every rule holds however the value arrived. An import racing a human edit resolves through the same optimistic lock as two humans racing.

**Harder:** the import path is slower than a raw upsert — load the aggregate, dirty-check, save. The data shape absorbs this: a file carries one row per metric per organization, so a 100,000-row file is a few hundred aggregate operations, not 100,000. If measurement ever says otherwise, the fallback is a stateless batch update *inside the submission module*, behind the same validation — not a second writer outside it.

**Accepted, and stated in the README:** a *global role* revoked after an import was admitted is not re-evaluated while that job runs. Re-checking it would need a persisted token (forbidden) or an IdP call per chunk (worse). Membership, which is what actually changes in practice, **is** re-checked per group against live data.

**Enforced, not just written down:** ArchUnit fails the build if `imports` reaches into `submission.domain`, `.application`, `.infrastructure` or `.api`; a source scan fails it if `imports` names a submission table in SQL; another fails it if any import class touches `SecurityContextHolder`; and a schema test fails it if a foreign key ever points from a submission table to an import one.

## Alternatives considered

**Upsert from the import module, with the aggregate for manual edits only.** Rejected: two writers, one set of invariants, and the second one wins whenever someone is in a hurry.

**Reconstruct an `Actor` on the batch thread from the job's creator.** Rejected as incoherent: roles are never persisted, so there is nothing to reconstruct them from. The failure would not have been a crash but an `Actor` with an empty role set quietly passing checks.

**Derive the idempotency key from the file checksum.** Rejected: it makes re-importing identical content impossible, which blocks the single most likely thing a user does after a failed import.
