# OpsFlow

A reporting and approval platform for organizations with distributed teams.

Units report defined metrics on a fixed cadence; a central reviewer approves each submission or
returns it with a reason; units that live in spreadsheets bulk-upload CSV instead of typing.
Built as a **modular monolith** — Java 21 · Spring Boot 4 · Angular 22 · PostgreSQL 18 · Keycloak.

> **Complete through M9.** The application works end to end: sign in, administer organizations,
> members, metrics and reporting periods, run a submission through its full review loop, bulk-load
> values from CSV, and see how far through a period its participants are.
> [`make demo`](docs/demo.md) seeds a dataset that shows all of it in about ten minutes.

## What it does

| Capability | What that means here |
|---|---|
| **Authentication** | Keycloak OIDC, Authorization Code + PKCE, audience-validated bearer tokens. No server session |
| **Authorization** | Keycloak owns *what kind of user*; the database owns *which organizations*. Roles are never persisted |
| **Metric catalogue** | Typed metrics — integer, decimal, percentage — with ranges, units and archival |
| **Reporting periods** | Metric scope *and* participating organizations, both frozen when the period opens |
| **Reporting** | One submission per organization per period, validated against the period's scope |
| **Review workflow** | Draft → submitted → under review → approved, plus return-with-reason, withdraw, claim and release |
| **Workflow history** | One row per real status change: who, when, why |
| **CSV import** | Streamed, chunked Spring Batch job that writes through the same aggregate as the form |
| **Period summary** | Status counts against the period's frozen participant set |
| **Concurrency** | Optimistic locking on every aggregate; the races application code cannot win are decided by database constraints |

## Quick start

Requires **Docker**, a **Java 21 JDK** and **Node ≥ 24.15.0**.

```bash
make preflight                        # check local tooling
make up                               # PostgreSQL + Keycloak
cd backend && ./gradlew bootRun       # terminal 2
cd frontend && npm start              # terminal 3
make demo                             # seed the demo dataset
```

Open `http://localhost:4200` and sign in as `admin`, `reporter` or `reviewer` — password
`password`. A `noroles` user exists for showing what an authenticated user with no role can reach.

`make demo-reset` starts over; it **deletes the local database volume**, so every organization,
submission and import goes with it. `make help` lists every target,
[`docs/demo.md`](docs/demo.md) is the walkthrough, and
[`docs/local-development.md`](docs/local-development.md) covers the daily loop.

## Architecture

One deployable, six business modules in a strict dependency order, plus a platform layer and a
thin composition layer:

```text
platform → identity → organization → reporting → submission → imports

composition → every module, through published APIs only; nothing depends on composition
```

The order is total, so cycles are impossible by construction. Each module holds
`domain` / `application` / `infrastructure` / `api` beneath its own root package, and that root —
the published API — is the only thing another module may reference.

Boundaries are enforced three ways, all failing the build rather than a review: **ArchUnit** on
the code, a **source scan** for SQL against another module's tables, and a **schema test** that no
foreign key points against the dependency direction. A diagram and the module table are in
[`docs/architecture.md`](docs/architecture.md).

The parts worth reading, each argued in an ADR rather than in comments:

- **One writer for submission data.** CSV import goes through the same aggregate, the same
  validation and the same optimistic lock as manual editing. Efficiency comes from grouping a
  chunk by organization, never from bypassing it — [ADR-0006](docs/adr/0006-imports-write-through-the-submission-aggregate.md).
- **Two-phase import authority.** Roles are checked once, at admission, against a real token;
  the job records the *basis* it was admitted on and re-checks membership and every mutable
  business rule while it runs — [ADR-0003](docs/adr/0003-identity-and-authorization.md).
- **Request-scoped idempotency.** `(user, Idempotency-Key)` is unique and the fingerprint covers
  the file, the period and the scope, so retrying a failed import with a new key works while a
  retried HTTP call does not run twice.
- **Scope freezes when a period opens.** Both the metric scope and the participating organizations
  are fixed at that moment, so a closed period's figures cannot shift when an organization is
  later created or archived — [ADR-0005](docs/adr/0005-workflow-inside-submission.md).
- **Identity we own.** The application mints its own `UserId`; the OIDC `sub` is a uniquely
  indexed attribute, not a primary key, so the identity provider is an integration rather than the
  owner of every foreign key — [ADR-0003](docs/adr/0003-identity-and-authorization.md).
- **Tokens never touch browser storage.** Only the token-free OIDC handshake state reaches
  `sessionStorage`, and a Playwright test asserts it — [ADR-0011](docs/adr/0011-browser-token-storage.md).
- **A compile-time API contract.** The Angular client is generated from the OpenAPI document with
  response fields marked required, so a field the server stops sending is a TypeScript error
  rather than a runtime `undefined`. CI fails if the committed client drifts from the spec.
- **One error contract.** Every failure is RFC 9457 `application/problem+json` with a stable
  machine-readable `code` and the request's correlation id — including 500s, which leak nothing
  else — [ADR-0010](docs/adr/0010-error-contract.md).
- **Honest recovery.** An import killed mid-flight is failed as *interrupted* with its last
  truthful counts, never silently resumed — [ADR-0009](docs/adr/0009-single-replica-imports-and-recovery.md).

## Repository layout

```
backend/     Spring Boot 4 modular monolith (Java 21, Gradle)
frontend/    Angular 22 application (standalone components, signals, no NgRx)
deploy/      Compose configuration, the Keycloak realm, the demo seeder
docs/        Architecture notes, ADRs, the demo walkthrough, sample CSVs
```

## Testing

| Layer | What runs |
|---|---|
| Domain | Plain JUnit over the aggregates, including the full state-machine matrix |
| Persistence & concurrency | Testcontainers against real PostgreSQL — no H2, because the schema depends on partial indexes, exclusion constraints and `ON CONFLICT` semantics |
| API & security | Per endpoint: unauthenticated `401`, wrong role `403`, cross-organization `404` |
| Architecture | ArchUnit, the cross-module SQL scan, and the schema foreign-key rule |
| Frontend | Vitest with the Angular TestBed |
| Browser | Playwright against a live stack — the full review loop, a CSV import, the summary, and a check that no token reaches browser storage |

`make verify` runs what CI runs. `make test` is just the test suites.

## Roadmap

| Milestone | Scope | Status |
|---|---|---|
| M0 | Repository, toolchain, CI | ✅ |
| M1 | Platform foundations: error contract, correlation IDs, Flyway, Testcontainers | ✅ |
| M2 | Security and identity: Keycloak, OIDC resource server, user provisioning | ✅ |
| M3 | Organizations and membership | ✅ |
| M4 | Metric catalogue and reporting periods | ✅ |
| M5 | Angular shell, authentication, admin screens | ✅ |
| M6 | Submissions and the review workflow | ✅ |
| M7 | CSV import with Spring Batch | ✅ |
| M8 | Period summary | ✅ |
| M9 | Demo dataset, complete CI, documentation | ✅ |

Deliberately **not** built, and not pretended: notifications, per-organization reviewer
assignment, export, Redis, Kubernetes manifests, Grafana dashboards, and horizontal scaling.

## Limitations

Stated up front, because a design that hides these is not finished:

- **Single replica.** Horizontal scaling is not implemented and not claimed: it needs object
  storage for import files and a job-ownership lease. Deploys incur brief downtime.
- **No notifications.** A reporter learns their submission was returned by signing in and looking.
- **Reviewers are global.** Any `REVIEWER` may review any organization; per-organization reviewer
  assignment is future work.
- **Workflow history is not a full audit log.** Status changes are recorded with actor, reason and
  time. Value-level before/after changes are not — after approval you can see who last touched a
  value, not what it was before.
- **Interrupted imports are not resumed automatically.** They are detected at startup, failed with
  accurate applied-row counts and shown as interrupted rather than rejected, and the user
  re-uploads — safe, because applying the same values twice converges. Cancelling or manually
  restarting an import is future work.
- **A global role revoked mid-import is not honoured.** Membership revocation is.
- **An organization archived after a period opens** stays in that period's figures. That is what
  "frozen" means: it did participate, and a completion figure that changes retroactively is wrong.
- **The period summary is a count, not analytics.** Status counts against the frozen participant
  set and an approved ratio. No trends, no forecasts, no charts.
- **A full page reload signs the user in again**, silently, and lands on the home screen. That is
  the cost of keeping tokens out of browser storage, and it is a deliberate trade.

## Documentation

- [Architecture](docs/architecture.md) — diagram, module table, how boundaries are enforced
- [Architecture Decision Records](docs/adr/) — eleven decisions, with amendments where later
  milestones changed one
- [Demo walkthrough](docs/demo.md) — ten minutes, end to end
- [Local development](docs/local-development.md) — the daily loop, running the checks, e2e

API documentation is served by the running application at `http://localhost:8080/swagger-ui.html`,
and the OpenAPI document at `/v3/api-docs`.

## Technology

Java 21 · Spring Boot 4.1.1 · Spring Batch 6 · PostgreSQL 18 · Keycloak 26.7 · Angular 22.1.7 ·
TypeScript 6.0.3 · Node 24 LTS · Gradle 9.7.1 · Testcontainers 2.x · Docker

Versions are pinned exactly. Rationale, including why the Spring Boot 3.x line is not an option,
is in [ADR-0007](docs/adr/0007-technology-versions.md).

## License

[MIT](LICENSE)
