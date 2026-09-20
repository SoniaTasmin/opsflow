## What and why

<!-- What changed, and the reason. The diff already shows the "what" in detail;
     use this space for the "why". -->

## How it was tested

<!-- Commands run, scenarios exercised. Screenshots for UI changes. -->

## Checklist

- [ ] Acceptance criteria for the milestone are met and demonstrable
- [ ] Domain rules covered by unit tests
- [ ] New endpoints have happy-path, validation, role **and cross-organization** tests
- [ ] Integration tests run against Testcontainers PostgreSQL (no H2)
- [ ] ArchUnit rules pass; any new module dependency is intentional and explained
- [ ] Migrations are forward-only; Hibernate validates against the migrated schema
- [ ] No JPA entity crosses the web boundary; no OIDC `sub` outside `identity`
- [ ] Errors use the standard problem+json contract with a stable `code`
- [ ] OpenAPI spec and the generated TypeScript client regenerated if endpoints changed
- [ ] No secrets, tokens, emails or request bodies in logs
- [ ] Linters and formatters clean; no new warnings
- [ ] ADR added if a decision was made; docs updated if setup or operations changed
