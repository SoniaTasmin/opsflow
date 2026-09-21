# OpsFlow

A reporting, approval and bulk-import platform for organizations with distributed teams.

Units report defined metrics on a fixed cadence; a central reviewer approves or returns each
submission with a reason; units that live in spreadsheets bulk-upload CSV instead. Built as a
**modular monolith** — Java 21 / Spring Boot 4 / Angular 22 / PostgreSQL 18 / Keycloak.

> **Status: M5 of 10 complete.** There is a working application: sign in through Keycloak and
> administer organizations, members, metrics and reporting periods in the browser. Submissions
> start at M6. See [the roadmap](#roadmap) for what does and does not exist.

## Why this project is interesting

Once there is something to look at, these are the parts worth reading:

- **Module boundaries enforced three ways** — ArchUnit rules on code, no cross-module SQL, and
  no foreign key pointing against the dependency direction. See
  [ADR-0001](docs/adr/0001-modular-monolith.md).
- **One writer for submission data.** CSV import goes through the same aggregate and the same
  invariants as manual editing; efficiency comes from grouping work, never from bypassing it.
- **Scope freezes when a period opens.** Both the metric scope and the participating
  organizations are fixed at that moment, so a closed period's figures cannot shift later when an
  organization is created or archived.
- **Concurrency handled in the database** — a unique constraint on (organization, period), a GiST
  exclusion constraint against overlapping periods, and a partial unique index serializing
  concurrent imports.
- **One role system, and identity we own.** Keycloak owns global roles and they are never
  persisted; the database owns organization scope. The application mints its own `UserId` rather
  than using the OIDC `sub` as a primary key, so the identity provider is an integration and not
  the owner of every foreign key. See [ADR-0003](docs/adr/0003-identity-and-authorization.md).
- **A compile-time API contract.** The Angular client is generated from the OpenAPI document;
  response fields are marked required, so a field the server stops sending becomes a TypeScript
  error rather than a runtime `undefined`.
- **Tokens never touch browser storage.** The OIDC client is backed by an in-memory store, so an
  XSS bug cannot read an access token back out of `localStorage`.
- **One error contract, enforced.** Every failure is RFC 9457 `application/problem+json` with a
  stable machine-readable `code` and the request's correlation ID — including 500s, which leak
  nothing else. See [ADR-0010](docs/adr/0010-error-contract.md).
- **Honest limitations.** The [limitations](#limitations) section below is part of the design,
  not an apology for it.

## Quick start

Requires **Docker**, a **Java 21 JDK**, and **Node ≥ 24.15.0**.

```bash
git clone <this repo> && cd opsflow
make preflight     # verify local tooling
make up            # start PostgreSQL
make test          # run backend and frontend tests
make build         # build both projects
```

`make help` lists every target.

To run the application itself: start the backend (`cd backend && ./gradlew bootRun`) and the
frontend (`cd frontend && npm start`), then open `http://localhost:4200` and sign in as
`admin` / `password`.

Once the stack is up, Keycloak is at `http://localhost:8081` (admin/admin) with the `opsflow`
realm imported. The local realm ships four users — `reporter`, `reviewer`, `admin` and
`noroles`, all with password `password` — so every role combination, including none at all, can
be exercised immediately.

Backend and frontend run from your IDE against the Compose infrastructure — that is the intended
daily loop. A full containerized demo stack with seed data arrives at M9 (`make demo`).

## Repository layout

```
backend/     Spring Boot 4 modular monolith (Java 21, Gradle)
frontend/    Angular 22 application (standalone components, signals)
deploy/      Container and infrastructure configuration
docs/        Architecture notes and Architecture Decision Records
```

Backend modules, in strict dependency order:

```
platform -> identity -> organization -> reporting -> submission -> imports -> composition
```

`composition` is a thin layer for endpoints shaped by what a client needs rather than by any one
capability — `/api/v1/me` is the first. It may depend on every module; nothing may depend on it.

The order is total, so cycles are impossible by construction and ArchUnit fails the build on any
back-edge.

## Roadmap

| Milestone | Scope | Status |
|---|---|---|
| M0 | Repository, toolchain, CI | ✅ Complete |
| M1 | Platform foundations: error contract, correlation IDs, Flyway, Testcontainers | ✅ Complete |
| M2 | Security and identity: Keycloak, OIDC resource server, user provisioning | ✅ Complete |
| M3 | Organizations and membership | ✅ Complete |
| M4 | Metric catalogue and reporting periods | ✅ Complete |
| M5 | Angular shell, authentication, admin screens | ✅ Complete |
| M6 | Submissions and the review workflow | Not started |
| M7 | CSV import with Spring Batch | Not started |
| M8 | Period summary | Not started |
| M9 | Demo stack, complete CI, documentation | Not started |

## Limitations

Stated up front, because a design that hides these is not finished:

- **Single replica.** Horizontal scaling is not implemented and not claimed: it needs object
  storage for import files and a job-ownership lease. Deploys incur brief downtime.
- **No notifications.** A reporter learns their submission was rejected by logging in and looking.
- **Reviewers are global.** Any `REVIEWER` may review any organization; per-organization reviewer
  assignment is future work.
- **Workflow history is not a full audit log.** Transitions are recorded with actor, reason and
  timestamp. Value-level before/after changes are not — after approval you can see who last
  touched a value, not what it was before.
- **Interrupted imports are not resumed automatically.** They are detected at startup, failed with
  accurate applied-row counts, and the user re-uploads.
- **A global role revoked mid-import is not honoured.** Membership revocation is.
- **An organization archived after a period opens** stays in that period's figures.

## Documentation

- [Architecture Decision Records](docs/adr/)
- [Local development](docs/local-development.md)

## Technology

Java 21 · Spring Boot 4.1.1 · Spring Batch 6 · PostgreSQL 18.6 · Keycloak 26.7.4 ·
Angular 22.1.7 · TypeScript 6.0.3 · Node 24 LTS · Gradle 9.7.1 · Testcontainers 2.x · Docker

Versions are pinned exactly and were last verified on **2026-09-20**. Rationale, including why
Spring Boot 3.x is not an option, is in [ADR-0007](docs/adr/0007-technology-versions.md).

## License

[MIT](LICENSE)
