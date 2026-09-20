# ADR-0004: URI-path API versioning

- **Status:** Accepted
- **Date:** 2026-09-20

## Context

The API has one consumer today (the Angular app, via a generated client) and may acquire others. A versioning scheme has to be chosen before the first endpoint ships, because retrofitting one is a breaking change by definition.

## Decision

Version in the URI path: `/api/v1/...`.

**A breaking change is:** removing or renaming a field, changing a field's type, changing the meaning of an existing `code` value in the error contract, adding a required request field, tightening validation on an existing field, or removing an endpoint.

**A non-breaking change is:** adding an optional request field, adding a response field, adding an endpoint, adding a new `code` value, or relaxing validation.

Non-breaking changes ship into `v1`. `v2` is created only when a breaking change is genuinely unavoidable, and the two versions then run side by side for a deprecation window.

## Consequences

**Easier:** trivially visible in logs, curl and browser address bars. Routing is a path prefix. Generated clients handle it without configuration. Cache keys differ naturally between versions.

**Harder:** a `v2` means duplicated controller routes, and the temptation is then to duplicate the layers beneath them. The mitigation is that versioning is a *web-layer* concern only: `v1` and `v2` controllers map to the same application services.

**Accepted:** URI versioning is not "pure REST" — a resource arguably should have one URI. The purity costs more than it returns.

## Alternatives considered

**Header versioning** (`Accept: application/vnd.opsflow.v1+json`). Theoretically cleaner. Rejected: invisible in logs and browser testing, easy for a client to omit, and it makes ordinary debugging require reading request headers.

**No versioning.** Rejected: a public-ish API with a generated client needs a defined story for breaking change, and "we will decide when it happens" means deciding under pressure.
