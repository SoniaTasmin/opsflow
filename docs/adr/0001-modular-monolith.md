# ADR-0001: Modular monolith with package modules and ArchUnit enforcement

- **Status:** Accepted
- **Date:** 2026-09-20

## Context

OpsFlow has six business capabilities (identity, organization, reporting, submission, imports, and cross-cutting platform concerns) and an expected scale of roughly 500 organizations, 300 metrics and 200 concurrent users. That is small.

The default instinct for a system described as "reporting, approval, batch import and analytics for distributed teams" is a set of services. The question is whether distribution buys anything here.

## Decision

Build a **modular monolith**: one deployable, one database, one transaction manager, with internal boundaries enforced by tests.

Modules are **Java packages**, not Gradle subprojects. Boundaries are enforced at three levels:

1. **Code** — ArchUnit rules assert the module dependency order and fail the build on any back-edge.
2. **Queries** — no module issues SQL against another module's tables; cross-module reads go through published ports.
3. **Schema** — no foreign key may point against the module dependency direction, asserted by a test over `information_schema`.

The module order is total, so cycles are impossible by construction:

```
platform -> identity -> organization -> reporting -> submission -> imports
```

## Consequences

**Easier:** one transaction spans a whole use case, which removes the single largest source of complexity in this domain — keeping a submission's status consistent with its values. One deploy, one log stream, one place to debug. Refactoring across a boundary is a compiler-checked rename rather than an API migration.

**Harder:** the boundaries are real only as long as the tests are. This is why the ArchUnit rules exist from M0, before there is anything to violate — boundary tests retrofitted onto an eroded codebase never actually get written.

**Accepted:** a shared failure domain, and a single scaling unit.

**What would justify extracting a service:** a capability needing independent scaling (imports outgrowing the request path is the plausible one), an independent release cadence driven by a separate team, or a genuinely different availability requirement. None of these are true today, and none are predicted to become true at the stated scale.

## Alternatives considered

**Microservices from the start.** Rejected: it would buy distributed transactions, network failure modes and six deployment pipelines in exchange for no requirement anybody has stated. It is the expensive way to demonstrate the same design skill.

**Gradle subprojects per module.** Genuinely tempting, because the compiler would enforce boundaries instead of a test. Rejected for now — see ADR-0002.

**No enforced boundaries, just discipline.** Rejected: this is how every ball of mud begins, and "we were careful" is not a mechanism.
