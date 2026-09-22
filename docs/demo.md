# Demo walkthrough

Ten minutes, start to finish. Every step below was run against a freshly seeded stack.

## Start

```bash
make preflight            # docker, a Java 21 JDK, node >= 24.15.0
make up                   # PostgreSQL and Keycloak
cd backend && ./gradlew bootRun     # in one terminal
cd frontend && npm start            # in another
make demo                 # seed the dataset, once the backend answers
```

Open `http://localhost:4200`. Sign in with `admin`, `reporter` or `reviewer` — password
`password` for all of them. `noroles` exists too, for showing what an authenticated user with no
role can reach (nothing).

To start over: `make demo-reset`. It deletes the local database volume, so every organization,
submission and import goes with it. Keycloak users come back from the realm import; application
data does not.

## What the seed contains

Six organizations participate in the open period **2026-Q1**, one in each state the summary
counts, so the whole workflow is visible without touching anything:

| Organization | 2026-Q1 submission |
|---|---|
| `BERLIN_OPS` | APPROVED |
| `LISBON_OPS` | REJECTED — waiting for a correction |
| `NAIROBI_OPS` | UNDER_REVIEW |
| `SINGAPORE_OPS` | SUBMITTED |
| `TORONTO_OPS` | DRAFT |
| `OSLO_OPS` | not started |

`reporter` belongs to `BERLIN_OPS` and `LISBON_OPS` only. `ZURICH_OPS` was created *after*
2026-Q1 opened, so it is deliberately absent from that period's frozen scope. **2025-Q4** is
closed with two approved submissions; **2026-Q2** is still a draft, so its scope is editable.

The catalogue has five metrics covering `INTEGER`, `DECIMAL` and `PERCENTAGE`, with ranges, and
both required and optional entries.

## The walkthrough

### 1. As `admin` — the administration surface

- **Organizations** — seven, with member counts. Open `BERLIN_OPS` → Members: `reporter` is there.
- **Metrics** — five, with value type and range. `SLA_COMPLIANCE` was created without limits and
  shows 0–100: a percentage is bounded by its type whatever the catalogue was told, and the same
  bound is applied again when a value arrives — including through a CSV.
- **Reporting periods** — `2026-Q1` is open, `2026-Q2` is a draft, `2025-Q4` is closed. Open
  `2026-Q1`: both scope editors are disabled, because a period's scope freezes when it opens.
  Open `2026-Q2` instead: both are editable, and the confirmation on **Open period** states what
  is about to be frozen.

### 2. As `reporter` — reporting

- **Submissions** lists two, not six: membership decides what exists as far as this user is
  concerned. Try a submission id belonging to another organization and the answer is 404, not 403
  — a 403 would confirm it exists.
- Open the **rejected** `LISBON_OPS` submission. The reviewer's reason is at the top, the values
  are editable again, and the timeline shows how it got here.
- Correct `OPEN_TICKETS`, **Save values**, then **Send for review**. The status moves to
  SUBMITTED and the fields lock.

### 3. As `reviewer` — reviewing

- **Review queue** shows everything waiting, across all organizations: a reviewer is central.
- Open the `LISBON_OPS` submission → **Claim review**. It is now held by you; a second reviewer
  gets 403 rather than a race.
- **Reject** with a reason to show the loop again, or **Approve** to finish it. The approver may
  not be the submitter — try it as `admin` on something `admin` submitted and the answer is 422.
- The timeline now carries one entry per real transition, with actor, time and the rejection
  reason. Saving values adds nothing: it is not a status change.

### 4. Period summary

**Period summary** → `2026-Q1`. As `admin` the denominator is 6 — the organizations frozen into
the period, not the seven that exist today. `ZURICH_OPS` is absent because it did not exist when
the period opened. Approve another submission, press **Refresh**, and the figures move.

Sign in as `reporter` and the same screen reads 2: the denominator narrows with visibility, so
the ratio is about their own organizations rather than a slice of someone else's.

### 5. CSV import

As `admin` (an administrator may load centrally for organizations they do not belong to):

- **CSV imports** → **New import** → period `2026-Q1`, organization *Any in the file*.
- Upload [`docs/samples/import-valid.csv`](samples/import-valid.csv): 6 rows, 6 applied, no
  errors. Open `TORONTO_OPS`'s submission and the values are there, marked as imported.
- Upload [`docs/samples/import-invalid.csv`](samples/import-invalid.csv): 8 rows, 2 applied, 6
  rejected, each named by line:

| Line | Problem |
|---|---|
| 3 | `ORG_UNKNOWN` — no organization has that code |
| 4 | `METRIC_UNKNOWN` — the period does not collect it |
| 5 | `VALUE_NOT_NUMERIC` |
| 6 | `VALUE_OUT_OF_RANGE` — 142 for a percentage |
| 7 | `SUBMISSION_NOT_EDITABLE` — `BERLIN_OPS` is approved, so it is untouched |
| 8 | `DUPLICATE_ROW` — a later row supersedes it |

The valid rows in that file still applied: one bad row does not fail the file.

Upload the same file again with the same file and period and it runs a second time, because the
browser sends a new idempotency key per upload. What does *not* run twice is one upload retried
over a flaky connection.

## Points worth making while demonstrating

- The import wrote through the same aggregate as the form. `BERLIN_OPS` being untouchable proves
  it: no separate bulk path could have known that rule.
- Kill the backend mid-import and restart it. The job comes back as **failed, interrupted** with
  its last truthful counts — not as completed, and not silently resumed.
- Keycloak decides *what kind of user* this is; the database decides *which organizations*. The
  two are never mixed, and roles are never stored.
