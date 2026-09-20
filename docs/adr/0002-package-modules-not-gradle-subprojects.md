# ADR-0002: Package-based modules enforced by ArchUnit, not Gradle subprojects

- **Status:** Accepted
- **Date:** 2026-09-20

## Context

ADR-0001 commits to a modular monolith. Module boundaries can be enforced two ways:

1. **Gradle subprojects** — the compiler refuses a dependency that is not declared. Strongest possible guarantee.
2. **Java packages + ArchUnit** — a test fails the build on a violation. Weaker: the code compiles, the test catches it.

## Decision

Use **packages plus ArchUnit** for now, and document the promotion path.

## Consequences

**Easier:** one build file, one dependency catalogue, one Spring context, one Flyway configuration, one `src/test` tree. Entity scanning, component scanning and migration locations all stay trivial. A junior contributor can find anything.

**Harder / weaker:** a violation is caught by a test rather than by the compiler, so it is caught seconds later and can in principle be suppressed by deleting a test. The mitigation is that the rules live in `backend/src/test/java/com/opsflow/architecture/` and deleting one is a visible, reviewable diff.

**Accepted:** slightly weaker enforcement in exchange for meaningfully less build machinery on a project whose main risk is not being finished.

**Promotion path.** Convert to Gradle subprojects when any of these happen: ArchUnit violations start appearing regularly in review; more than one person is working in the codebase simultaneously; or a module genuinely needs to be published or deployed on its own. The package structure is already shaped so the conversion is mechanical — each `com.opsflow.<module>` becomes a subproject with its module-root package as its API.

## Alternatives considered

**Gradle subprojects now.** The better long-term answer and the more impressive one on paper. Rejected because the build complexity lands immediately while the benefit is proportional to team size and codebase age, both of which are currently one and zero.

**Spring Modulith.** Purpose-built for exactly this, with boundary verification and documentation generation included. Rejected as an additional runtime dependency and conceptual layer whose main feature — module verification — ArchUnit already provides in about forty lines. Worth revisiting if event-driven module communication is ever reintroduced.
