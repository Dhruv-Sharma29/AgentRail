# AgentFlow architecture

## Implemented modules

| Module | Responsibility | Boundary |
| --- | --- | --- |
| auth | Persisted users, BCrypt passwords, session login, logout, CSRF | Spring Security establishes the principal; requests cannot select ownership |
| workflow | DAG validation, owner-scoped CRUD, immutable version history | Controllers pass the principal to transactional services; repositories scope object queries to owner |
| execution | Run lifecycle, local actions, durable checkpoints, cancellation, audit events | Run creation validates a saved version; the worker serializes transitions on a run row |
| common | Safe controller error responses | Malformed requests are 400, missing/unowned resources 404, stale writes 409 |

Agent, tool-policy, and approval modules are planned. Execution owns the initial audit records; a general audit module will expand to authentication and administration events later.

## Versioning and transactions

A workflow is a mutable metadata record with owner, current version, archive state, and an optimistic revision counter. Each successful create/update writes a separate version containing the complete definition and metadata snapshot. The application exposes no mutation endpoint for historical versions; version columns are marked non-updatable in JPA.

Creation writes workflow and initial version in one transaction. Update locks the owned workflow row, checks expectedVersion, validates the graph, changes metadata, and writes the next version in one transaction. The `(workflow_id, number)` database constraint prevents duplicate version numbers. Concurrent edit tests require one success and one conflict. Archive shares the same row lock and preserves versions.

Lists use bounded pagination. Details fetch only the current version, while history pages definitions oldest first. There are no lazy relationships serialized as API output and no network calls inside these transactions.

## Database and deployment

Flyway creates users, workflows, workflow_versions, workflow_runs, node_executions, and audit_events. Hibernate validates the schema. The explicit dev profile uses file-backed H2 for standalone local work. The default profile uses PostgreSQL with required environment configuration. Local Compose binds exposed ports to loopback; Secure session cookies remain enabled in the default application configuration.

The bootstrap username is created only if absent. It is a USER account, and password changes in the environment do not reset existing credentials. Registration and credential administration require a future authenticated design.

## Deterministic execution

Run creation locks the owned workflow, resolves an explicit immutable version, validates executable semantics, and persists the run and ordered step plan in one transaction. The unique `(owner_id, workflow_id, idempotency_key)` constraint and workflow lock serialize duplicate starts. Replays also lock the run when building details. Subsequent workflow edits or archives do not alter the bound version or plan.

The scheduled worker reads at most 20 PENDING/RUNNING IDs and calls the transactional service once per ID. Each call locks the run and advances one pure local action. The run checkpoint, step outcome, and audit events commit together. There is no persisted in-flight step state: a process death before commit rolls everything back; after commit the next worker skips that completed step. Concurrent workers and cancellation share the run lock. Trace reads use a repeatable-read transaction to keep run, step and event snapshots consistent.

The only handlers receive input, classify by local rules, search a bounded built-in catalog, and record a local result. Issue text is data; it cannot choose a URL, shell command, tool, owner, or approval. No external effects occur under database locks. Handler failures terminate the run and cancel remaining steps. Database transaction failures preserve the previous checkpoint for the next poll.

`audit_events` contains append-only lifecycle and step events with a unique per-run sequence. Actor ownership is available through the immutable run owner. It is not a complete security audit system yet. Submitted issue content and checkpoint snapshots are stored in run/step records and exposed only to their owner.

The process-restart script tests an abrupt Java process death against PostgreSQL and fresh-process recovery. Transaction rollback and worker races are also tested against both H2 and PostgreSQL. These guarantees apply to the current local handlers; a database transaction cannot provide exactly-once external execution.

V2 is additive. Disable the worker to stop automatic advancement while preserving history. Any rollback artifact must retain V2's migration metadata; the older V1-only artifact has not been tested against a V2 database. Schema rollback requires a database backup/restore; dropping execution tables discards run and audit history and is not an automatic migration step.

## Runtime extension

The next slice adds a planner that returns structured proposals and a Java policy boundary that authorizes registered tools and validates arguments. Sensitive effects pause with an immutable proposal before execution. Remote calls must occur outside database locks, with durable intents, tool-specific idempotency, and reconciliation for uncertain outcomes.

Live providers, external tools, approval decisions, branching semantics, and action retries remain future work. Persisting AI/action node labels has no side effect.
