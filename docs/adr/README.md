# Architecture Decision Records

One file per decision, in [Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
form. They are immutable once accepted: where a later milestone changed a decision, the ADR
carries an **amendment** saying what changed and why, rather than being rewritten to look
prescient.

| # | Decision | Amended |
|---|---|---|
| [0001](0001-modular-monolith.md) | Modular monolith over microservices; boundaries enforced at three levels — code, queries, schema | |
| [0002](0002-package-modules-not-gradle-subprojects.md) | Package modules with ArchUnit, not Gradle subprojects | |
| [0003](0003-identity-and-authorization.md) | Keycloak owns roles, the database owns organization scope, the application owns `UserId`; two-phase authority for asynchronous work | |
| [0004](0004-api-versioning.md) | URI-path versioning (`/api/v1`) and what counts as a breaking change | |
| [0005](0005-workflow-inside-submission.md) | The review workflow lives inside `submission`; period scope freezes on open | M6 — review stayed in the aggregate · M8 — the summary moved to `composition` |
| [0006](0006-imports-write-through-the-submission-aggregate.md) | CSV import writes through the submission aggregate; request-scoped idempotency | |
| [0007](0007-technology-versions.md) | Spring Boot 4.1 / Java 21 / Angular 22, and why 3.x is not an option | |
| [0008](0008-stateless-bearer-token-security.md) | Stateless bearer tokens; CSRF disabled, with a tripwire | |
| [0009](0009-single-replica-imports-and-recovery.md) | One replica, local import storage, recovery that does not pretend | |
| [0010](0010-error-contract.md) | RFC 9457 errors with a stable machine-readable code | |
| [0011](0011-browser-token-storage.md) | Tokens in browser memory; only token-free handshake state is stored | |

[`template.md`](template.md) is the starting point for the next one.
