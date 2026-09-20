# ADR-0008: Stateless bearer-token security, with CSRF protection disabled

- **Status:** Accepted
- **Date:** 2026-09-21

## Context

The API is consumed by a single-page application that authenticates against Keycloak with
Authorization Code + PKCE. Two decisions follow from that and need recording, because one of
them looks alarming out of context.

## Decision

### Stateless

`SessionCreationPolicy.STATELESS`. No `JSESSIONID`, no server-side session, no sticky sessions.
The token is the entire request state, so any instance can serve any request.

Every endpoint is closed unless explicitly opened: the filter chain ends in `denyAll`. A newly
added controller is unreachable until someone deliberately decides who may reach it. The reverse
default — open unless secured — means a forgotten rule is a vulnerability instead of a 403.

Health endpoints are `permitAll`, because gating a liveness probe on the very authentication
system that might be failing turns a dependency outage into a restart loop. Everything else under
`/actuator/**` is `denyAll` at the security layer *in addition to* not being exposed by the
management configuration — two independent gates, because `/actuator/env` disclosing
configuration is a bad enough outcome to justify redundancy.

### Token validation

A token is accepted only if **all** of the following hold: the signature verifies against the
realm's JWKS, the issuer matches the configured one, it is within its validity window (with a
small clock-skew tolerance), and **`aud` contains `opsflow-api`**.

The audience check is the one that is easy to omit and expensive to omit. Signature and issuer
validation prove a token is genuine, not that it was meant for us. Without it, any validly-signed
token from the realm is accepted — including one a user consented to give a completely different
client. That is audience confusion, and it is why the claim exists.

The JWKS URI is configured explicitly rather than discovered from the issuer. Discovery is a
network call *during startup*, which makes a reachable identity provider a precondition for the
application booting at all — and, less obviously, made the test suite silently depend on a local
Keycloak to load an application context. With the URI configured, signing keys are fetched on
first use and cached. Issuer, signature, validity and audience are validated either way.

Roles are read from `realm_access.roles` on every request and mapped to authorities, ignoring
anything that is not one of this application's three roles. Keycloak issues roles of its own and
a realm may carry roles for other applications; an authority the application does not understand
is one it cannot reason about.

### CSRF protection is disabled

CSRF exists because browsers attach **ambient** credentials — cookies — to cross-site requests
automatically. This API has no ambient credentials: it authenticates solely through an
`Authorization` header that JavaScript must set deliberately, and issues no session cookie. A
cross-site form post therefore carries nothing and is simply unauthenticated. A CSRF token here
would add a round trip and protect against nothing.

**Tripwire:** if authentication ever moves to cookies — a backend-for-frontend, a "remember me"
feature, anything at all — CSRF protection must be re-enabled **in the same commit**. This is
written into the ADR and repeated as a comment at the `csrf(...)` call, because "we disabled CSRF
once for good reasons and nobody revisited it" is how real vulnerabilities are born.

### CORS

Exact origins from configuration, never a wildcard and never a reflected `Origin` header.
`allowCredentials` is `false`: bearer tokens mean the browser never needs to attach credentials,
and `allowCredentials=true` combined with a permissive origin is the classic CORS foot-gun.

### Security headers

`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, HSTS with subdomains, and
`Referrer-Policy: no-referrer`. The CSP is `default-src 'none'; frame-ancestors 'none'`: this is
a JSON API that serves no scripts, styles or frames, so the most restrictive policy possible is
also a correct one.

## Consequences

**Easier:** any instance serves any request. No session replication, no sticky load balancing,
and horizontal scaling is limited by other things (see ADR-0009) rather than by sessions.

**Accepted risk:** the SPA holds an access token in browser memory, so an XSS bug in the frontend
can exfiltrate it. Mitigated by a five-minute access-token lifetime, refresh-token rotation with
reuse detection, Angular's default output escaping, and a prohibition on
`bypassSecurityTrust*` with user data. **Not eliminated.** The genuinely secure alternative is a
backend-for-frontend holding tokens server-side behind an `HttpOnly` cookie — which would make
the backend stateful and require CSRF protection, and is a larger change than this project's
scope. It is the right answer for regulated data, and it is recorded here as the known upgrade
path rather than pretended away.

**Accepted:** the local realm ships two development-only clients (`opsflow-dev-cli`,
`opsflow-wrong-audience-cli`) with direct access grants enabled, so a developer can obtain a
token with curl and an integration test can prove that a token lacking this API's audience is
rejected. The production-shaped client, `opsflow-frontend`, keeps direct grants **off** and is
exactly as it would be deployed. A deployed realm would not include the dev clients.

## Alternatives considered

**Backend-for-frontend with cookie sessions.** More secure against XSS, and rejected only on
scope: stateful backend, session store, CSRF re-enabled. Recorded above as the upgrade path.

**Opaque tokens with introspection.** Immediate revocation, at the cost of a network call to
Keycloak on every request and a hard runtime dependency on it. Rejected: short token lifetimes
give most of the benefit without putting the identity provider in the path of every request.

**Skipping audience validation** because there is only one client today. Rejected: it costs three
lines, and the failure mode it prevents is silent.
