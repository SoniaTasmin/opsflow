# ADR-0011: Tokens live in browser memory; only token-free handshake state is stored

- **Status:** Accepted
- **Date:** 2026-09-21

## Context

The SPA authenticates with Authorization Code + PKCE and then holds an access token to call the
API. Where that token lives is the single largest decision about the blast radius of an XSS bug
in the frontend.

`angular-auth-oidc-client` defaults to `sessionStorage`, and every tutorial uses it. Anything in
`localStorage` or `sessionStorage` is readable by any script running on the origin, so one
injected script is one `fetch` away from exfiltrating a valid bearer token — and the user cannot
tell, because the token keeps working until it expires.

## Decision

Tokens are held **in memory only**, behind a custom `AbstractSecurityStorage`. Nothing
token-bearing is written to `localStorage` or `sessionStorage`.

The storage is a hybrid rather than purely in-memory, because purely in-memory does not work.
The library writes its handshake state — the PKCE code verifier, the `state` value, the target
route — *before* redirecting to Keycloak, and reads it back *after* the browser has returned.
That is a full page load: memory is gone by then, and the callback fails with "could not find
matching config for state".

So the storage splits by key. Every token-bearing key (`token`, `authzData`, `authnResult`,
`userData`) stays in memory. The remaining handshake keys, which carry no credential, are
mirrored into `sessionStorage` so they survive the redirect. Reads merge the two, memory first.

## Consequences

**Gained:** an XSS bug cannot read an access or refresh token out of storage. Combined with a
`default-src 'none'` CSP, that is a meaningful reduction in what a frontend injection is worth.

**Cost, and it is a real one:** a full page reload discards the token, so the user is
re-authenticated silently through Keycloak's session. That is invisible when it works, and it
means a deep link followed by a hard refresh lands on the home screen rather than the requested
page — the route is lost with the memory that held it. The e2e suite navigates through the
application's own links for exactly this reason.

**Verified, not assumed:** a Playwright test signs in for real and then asserts that neither
`localStorage` nor `sessionStorage` contains anything token-shaped. A regression here would be
silent otherwise.

**Unchanged by this decision:** the backend is the security boundary. Route guards in the SPA are
a usability affordance; every admin screen they hide is also refused server-side.

## Alternatives considered

**`sessionStorage` for everything.** Rejected: it is the default, it is convenient, and it hands
a bearer token to any injected script.

**HttpOnly cookies with a BFF.** The genuinely stronger answer — the token never reaches
JavaScript at all. Rejected for this project because it means a server-side session, a backend-for-
frontend to hold it, and CSRF protection coming back with cookie authentication (ADR-0008). That
is a different architecture, not a setting, and it is recorded here as the upgrade path rather
than pretended away.

**Refresh tokens in storage so reloads are seamless.** Rejected outright: a refresh token is
worth more than an access token, and storing it to avoid one silent redirect is a poor trade.
