# ADR-0003: Keycloak owns global roles, the database owns organization scope

- **Status:** Accepted
- **Date:** 2026-09-20

## Context

Two questions must be answered on every request: *what kind of user is this* (may they review? administer?) and *which organizations may they act for*.

An earlier draft answered both twice: Keycloak realm roles carried `USER`/`REVIEWER`/`ADMIN`, and `org_membership` carried a per-membership role from the same set. Nothing defined which won when they disagreed. Two sources of truth for authorization is not a modelling preference; it is a defect waiting for its first ambiguous case.

That draft also used the OIDC `sub` claim directly as the `app_user` primary key. That works only because Keycloak happens to mint UUID subjects — the specification permits any string up to 255 characters — and it makes the identity provider the owner of every foreign key in the system.

## Decision

**One role system.** Keycloak realm roles are the only source of "what kind of user":

| Role | Means |
|---|---|
| `USER` | May work with the organizations they belong to |
| `REVIEWER` | Central reviewer: review authority across all organizations |
| `ADMIN` | Platform administration |

Roles are read from the token on every request and **never persisted**. `app_user` has no role column.

**The database owns scope.** `org_membership` associates a user with organizations and carries no role.

**Application-owned user identity.** `app_user.id` is an application-generated UUIDv7, and it is
typed: `UserId` is part of the identity module's published API, and every cross-module contract
speaks in `UserId` rather than raw `UUID`. Every identifier in this system is a UUID underneath,
so a raw one in a contract is a parameter the compiler cannot check — passing an organization id
where a user id belongs would compile perfectly. Unwrapping happens only at the persistence and
JSON boundaries, and an ArchUnit rule keeps it that way. The external identity is an attribute: `UNIQUE (oidc_issuer, oidc_subject)`, both `varchar(255)`. Users are provisioned just-in-time on first authenticated request.

**Two-phase authority for asynchronous work.** Roles live in a token and cannot be re-evaluated on a batch thread. So for imports: global role authorization happens once, at admission, against a verified token, and the basis is recorded as `authorization_mode ∈ {ORGANIZATION_MEMBER, ADMIN_OVERRIDE}`. During processing, everything that lives in the database — membership, organization status, period participation and state, submission editability, metric scope, value ranges — is re-checked against live data. The type carrying this (`ImportAuthority`) has no roles on it at all, and `Actor` is kept out of the imports module by an ArchUnit rule.

## Consequences

**Easier:** one place to change what a role means. Migrating or federating identity providers no longer means rewriting foreign keys. A stale role copy — a privilege-escalation bug with a delay fuse — is structurally impossible.

**Harder:** one extra indexed lookup per authenticated request to resolve `(issuer, subject)` to a `UserId`. The mapping is immutable, so an in-process cache is the obvious fix; it is deliberately **not** built until measurement at M2 justifies it.

**Accepted limitation:** a global role revoked mid-import is not honoured — the job continues under its admission decision. Closing this would require persisting a token (forbidden) or calling Keycloak from inside a batch transaction (a hard IdP dependency in the worst possible place). Imports run for minutes, so the window is small and bounded. Membership revocation *is* honoured, and membership is what actually changes in practice.

**Accepted limitation:** if an identity provider deletes a user and later reissues the same subject to a different person, they inherit the original `UserId`. Mitigation: `app_user` rows are never deleted and `(issuer, subject)` is treated as permanently bound.

**Watch item:** `authorization_mode` is the one place an authorization decision is persisted. It is written once from a verified token and read only by the import's membership check. If it ever gains a second reader, it has become a cached role and should be deleted.

## Alternatives considered

**Organization-scoped roles in the database.** Rejected: the dual-source-of-truth problem above.

**Roles as token claims including organization memberships.** Rejected: memberships change often and are business data. Encoding them in tokens means reissuing tokens on every membership change and bloating every request for users in many organizations.

**Using `sub` as the primary key.** Rejected: it hands primary-key ownership to an external system on the basis of an implementation detail of one IdP.

**Reconstructing a full `Actor` for batch work.** Rejected because it is impossible: roles are not persisted, so there is nothing to reconstruct from. Attempting it yields an empty role set that silently passes checks written to fail open.
