# ADR-0007: Technology version matrix

- **Status:** Accepted
- **Date:** 2026-09-20

## Context

The initial brief named "Spring Boot 3" and "Angular with Angular Material", and an early draft wrote versions as vague ranges such as "Angular 19+". Both needed checking against the actual release calendar before a single dependency was added.

Checking found that **the entire Spring Boot 3.x line is end-of-life**: 3.5 was the last 3.x branch and its OSS support ended on 2026-06-30 with final patch 3.5.16. Shipping a portfolio project on an unsupported framework line would be a worse signal than any version-currency benefit it might carry.

## Decision

Pin these exactly. No ranges, no `+`, no dynamic versions.

### Backend

| Component | Version | Note |
|---|---|---|
| Java | 21 (LTS) | Spring Boot 4.1 supports 17–26 |
| Gradle | 9.7.1 | Boot 4.1 supports 8.14+ and 9.x |
| Spring Boot | 4.1.1 | 3.x is EOL |
| Spring Framework | 7.0.9+ (managed) | Jakarta EE 11, Servlet 6.1 |
| Spring Security | 7.0.x (managed) | starter renamed `spring-boot-starter-security-oauth2-resource-server` |
| Spring Batch | 6.0.x (managed) | see the warning below |
| Jackson | 3.x (managed) | package root is `tools.jackson.*`, not `com.fasterxml.jackson.*` |
| JUnit | Jupiter 6.x (managed) | Boot 4 removes JUnit 4 support entirely |
| Testcontainers | 2.x | with `@ServiceConnection` |
| ArchUnit | 1.4.1 | **core only** — see below |
| Spotless | 7.0.4 | with palantir-java-format 2.68.0 |
| PostgreSQL | 18.6 | 19 is in beta |
| Keycloak | 26.7.4 | |

### Frontend

| Component | Version | Note |
|---|---|---|
| Angular | 22.1.7 | released 2026-06-03 |
| TypeScript | 6.0.3 | Angular 22 requires `>=6.0.0 <6.1.0` — **`typescript@latest` is 7.x and will not work** |
| Node | 24.x LTS (≥ 24.15.0) | Active LTS |
| Vitest | 4.1.11 | the Angular 22 CLI's default test runner |
| ESLint | 10.11.0 | with angular-eslint 22.5.0 |
| Prettier | 3.9.8 | |

## Consequences

**Java 21 is kept**, per instruction, and there is no strong reason to move: Boot 4.1 supports it and it is an LTS with a long runway. Java 25 LTS is available if a future need appears.

**Three Spring Boot 4 changes are not cosmetic and will bite if missed:**

1. **Spring Batch 6 defaults to an in-memory, "resourceless" job repository.** Database-backed metadata requires the `spring-boot-starter-batch-jdbc` starter and `@EnableJdbcJobRepository`. Missed, there is no restart metadata at all — which would silently invalidate the entire import recovery design.
2. **Jackson 3** means the package root is `tools.jackson.*`. Any ArchUnit rule or import referring to `com.fasterxml.jackson` is looking at the wrong thing.
3. **Flyway now needs its own dedicated starter**; the bare dependency is no longer auto-configured.

Also: Actuator liveness and readiness probes are enabled by default in Boot 4, which removes configuration the earlier draft assumed was necessary.

**ArchUnit core, not `archunit-junit5`.** The JUnit 5 extension (`@AnalyzeClasses`) is built against the JUnit Platform 1.x API and has not been verified against Platform 6. Plain Jupiter tests invoking `ArchRule.check(...)` give identical coverage with no compatibility exposure.

**Accepted cost:** Spring Boot 4 is current and correct but young. There is thinner community material than for Boot 3, so implementation leans on primary documentation more than on Stack Overflow answers. This is a real cost, not a free upgrade.

**Re-verification.** This matrix moved between drafts and will move again. It is re-verified before any dependency upgrade, Dependabot runs weekly, and the README records the last-verified date.

## Alternatives considered

**Staying on Spring Boot 3.5.** Rejected: unsupported since June 2026. A portfolio project on an EOL framework invites exactly the wrong question.

**Spring Boot 4.2.0-M1.** Rejected: a milestone, not GA.

**Angular 21.** Still supported and slightly better documented. Rejected narrowly in favour of 22, which is the current stable release with the longest remaining support window.

**PostgreSQL 19.** In beta at time of writing. Rejected.
