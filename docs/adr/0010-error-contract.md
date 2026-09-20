# ADR-0010: RFC 9457 problem details as the single error contract

- **Status:** Accepted
- **Date:** 2026-09-20

## Context

Every endpoint can fail, and a client has to react differently to "you sent something invalid",
"someone else changed this first" and "we broke". Without a decision made once, each controller
invents its own error shape, clients parse error *messages*, and rewording a message becomes a
breaking change.

The Angular client is generated from the OpenAPI document, so the error shape is part of the
compiled contract, not an afterthought.

## Decision

Every non-2xx response is `application/problem+json` (RFC 9457) with the same shape:

```json
{
  "type": "urn:opsflow:problem:version-conflict",
  "title": "Version conflict",
  "status": 409,
  "detail": "Submission was modified concurrently",
  "instance": "/api/v1/submissions/9f1c.../approve",
  "code": "VERSION_CONFLICT",
  "correlationId": "2f8c...",
  "timestamp": "2026-09-20T14:32:11.482Z",
  "errors": [{ "field": "amount", "code": "MIN_VALUE", "message": "...", "rejectedValue": -5 }]
}
```

- **`code` is the machine contract.** Clients switch on it. Renaming a code is a breaking API
  change; rewording `title` or `detail` is not.
- **`correlationId` is present on every error, including 500s**, and is echoed in the
  `X-Correlation-Id` response header. It is what a user reads out to support.
- **`errors[]` appears only for multi-field problems.**

### Why `type` is a URN, not an HTTP URL

RFC 9457 does not require the `type` URI to be dereferenceable, but clients and tooling
reasonably expect one that looks like `https://` to resolve. The project does not own
`opsflow.dev` and does not serve problem-type documentation, so publishing such URIs would be a
promise it cannot keep — and a broken link in every error response is worse than no link.

`type` is therefore a URN: `urn:opsflow:problem:version-conflict`. It identifies the problem
kind stably without implying a fetchable definition. **`code` remains the primary
machine-readable contract**; `type` exists to satisfy the RFC and to give the problem kind a
globally unique identifier.

If a documentation site is ever published under a domain the project owns, switching to HTTPS
URIs is a one-line change in `ErrorCode.typeUri()` — and at that point the URIs would actually
resolve.

### `rejectedValue` is never populated by the generic handler

`ApiFieldError` can carry the submitted value, but the **global validation handler never sets
it**, and the field is omitted from the response when null.

That handler sees every request body in the application. It cannot know which field holds a
password, a token, a personal detail or a free-text note, because it operates on whatever failed
Bean Validation. Reflecting the submitted value back would copy that content into the response
body, and from there into anything that records responses — browser history, proxy logs, error
trackers, support tickets. The field name and a reason code are enough for a client to highlight
the offending input, which is what the value was there for.

A **domain-specific** handler may set `rejectedValue` explicitly when it knows the value is a
safe scalar (a metric amount, a page size) and including it genuinely helps. That is an opt-in
decision made with knowledge of the field, never a default applied to everything.

Errors are raised as a **sealed** `DomainException` hierarchy — `NotFoundException`,
`ConflictException`, `BusinessRuleException`, `AccessDeniedDomainException` — each carrying an
`ErrorCode`. The domain throws these and nothing else: no bare `IllegalArgumentException` for a
business rule, and no `ResponseStatusException`, which would put HTTP concerns in the domain.

`ErrorCode` carries its HTTP status as a plain `int`, not Spring's `HttpStatus`, so the enum that
domain exceptions reference stays free of framework types.

### The 409 / 422 rule

Written down once so it stops being re-litigated in review:

- **409 Conflict** — the resource's current state conflicts with the request. *Reloading and
  retrying may succeed.* Optimistic-lock failures, uniqueness violations, illegal state
  transitions.
- **422 Unprocessable Content** — the request is well formed but a business rule rejects it.
  *Retrying it unchanged will always fail.* Missing required metrics, out-of-range values,
  self-approval.

A `BusinessRuleException` reports its own specific rule code (`REQUIRED_METRICS_MISSING`) as
`code`, rather than the generic enum constant, because that is what a client needs to act on.

### What never reaches the client

A 500 carries a generic message and the correlation id — never a stack trace, exception class
name, SQL statement or constraint name. `server.error.include-stacktrace` and `include-message`
are `never` in **every** profile, including local, so nobody can accidentally build against
leaked detail.

Client errors (4xx) are logged at `WARN` without a stack trace. A misbehaving client is not a
server incident and must not be able to fill the error log or page anyone.

### Existence hiding

`NotFoundException` is returned both when a resource does not exist and when it exists but the
caller cannot see its owning organization. Returning 403 for the second case would confirm that
a resource with that identifier exists. The real reason is logged with the correlation id, so
support can still tell the two apart.

## Consequences

**Easier:** one shape to document, one interceptor to write on the client, and a stable
vocabulary that survives message rewording. `correlationId` on every error makes a support
report actionable without asking the user to reproduce anything.

**Harder:** every new failure mode needs a deliberate `ErrorCode` or rule code rather than an
ad-hoc message, and the 409/422 judgement has to be made explicitly. That is the cost of the
contract, and it is the point.

**Accepted:** the `code` vocabulary is now versioned API surface. Adding codes is safe; renaming
one is not.

## Alternatives considered

**Ad-hoc JSON per endpoint.** Rejected: clients end up parsing prose.

**Spring's bare `ProblemDetail` with no extensions.** Rejected: no stable machine-readable code
and no correlation id, which are the two fields that actually get used.

**Exceptions carrying HTTP status directly** (`ResponseStatusException`). Rejected: it puts
transport concerns into domain code and makes the same rule untestable without a web context.
