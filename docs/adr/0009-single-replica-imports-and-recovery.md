# ADR-0009: One replica, local import storage, and recovery that does not pretend

- **Status:** Accepted
- **Date:** 2026-09-21

## Context

Imports hold state outside the database — the uploaded file — and they run asynchronously, so a process death leaves work in an ambiguous state. Both facts have to be dealt with honestly rather than papered over.

A persisted Spring Batch `JobRepository` lets a job *instance* be restarted. It does not cause anything to *re-launch*. Jobs are launched from a bounded in-JVM executor, so if the JVM dies, queued jobs are lost and in-flight ones leave an execution marked `STARTED` with no thread behind it.

## Decision

**One application replica.** No horizontal scaling is claimed. Uploaded files live on local disk behind an `ImportFileStore` port, with a second implementation used by the integration tests — two live implementations, which is what makes it a seam rather than a guess.

**Recovery, at startup, in one transaction under a PostgreSQL advisory lock:**

| Found | Action |
|---|---|
| `RUNNING` | Failed with `INTERRUPTED_BY_SHUTDOWN`, keeping the counters last committed. **Not resumed.** |
| `PENDING` | Re-enqueued. It never started, and the file is still in the store. |

The advisory lock turns the single-replica assumption into something enforced: a second instance finds it taken, logs, and does nothing.

## Consequences

**What is claimed:** an interrupted import is detected at startup, failed with truthful applied-row counts, and shown to the user as interrupted rather than rejected. Re-uploading is safe, because applying the same values again converges to the same state.

**What is not claimed, and says so in the README:** automatic resumption from the last committed chunk. A manual restart or cancel endpoint is future work.

**Accepted:** deploys incur brief downtime, and a normal `SIGTERM` lets the running chunk finish before the executor stops.

**What horizontal scaling would need**, neither of which is built: object storage for import files, and a job-ownership lease so two replicas cannot recover or run the same job.

## Alternatives considered

**Resume from the last committed chunk.** Rejected: the reader's position is not the same as what was applied, and guessing produces silent double-application or silent gaps. Failing loudly with accurate counts is worth more than an optimistic resume.

**Keep files in an `emptyDir` or in memory.** Rejected: a `PENDING` job would have nothing left to process after a restart, which makes the recovery table above a lie.

**A distributed lock service.** Rejected for one replica: a PostgreSQL advisory lock costs nothing and the database is already the single point of coordination.
