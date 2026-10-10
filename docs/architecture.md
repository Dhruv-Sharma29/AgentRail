# AgentFlow architecture

## Implemented modules

| Module | Responsibility | Boundary |
| --- | --- | --- |
| auth | Persisted users, BCrypt passwords, session login, logout, CSRF | Spring Security establishes the principal; requests cannot select ownership |
| workflow | DAG validation, owner-scoped CRUD, immutable version history | Controllers pass the principal to transactional services; repositories scope object queries to owner |
| execution | Run lifecycle, local actions, durable checkpoints, cancellation, audit events | Run creation validates a saved version; the worker serializes transitions on a run row |
| agent | Deterministic proposals and strict proposal policy | The planner receives bounded issue/context data and returns JSON; it cannot execute tools |
| tools | Code-controlled read-only tool registry, roles, schemas and context validation | Java validates names and typed arguments; workflow/model data cannot register capabilities |
| common | Safe controller error responses | Malformed requests are 400, missing/unowned resources 404, stale writes 409 |

Approval and live-provider modules are planned. Execution owns the initial audit records; a general audit module will expand to authentication and administration events later.

## Versioning and transactions

A workflow is a mutable metadata record with owner, current version, archive state, and an optimistic revision counter. Each successful create/update writes a separate version containing the complete definition and metadata snapshot. The application exposes no mutation endpoint for historical versions; version columns are marked non-updatable in JPA.

Creation writes workflow and initial version in one transaction. Update locks the owned workflow row, checks expectedVersion, validates the graph, changes metadata, and writes the next version in one transaction. The `(workflow_id, number)` database constraint prevents duplicate version numbers. Concurrent edit tests require one success and one conflict. Archive shares the same row lock and preserves versions.

Lists use bounded pagination. Details fetch only the current version, while history pages definitions oldest first. There are no lazy relationships serialized as API output and no network calls inside these transactions.

## Database and deployment

Flyway creates users, workflows, workflow_versions, workflow_runs, node_executions, and audit_events. Hibernate validates the schema. The explicit dev profile uses file-backed H2 for standalone local work. The default profile uses PostgreSQL with required environment configuration. Local Compose binds exposed ports to loopback; Secure session cookies remain enabled in the default application configuration.

The bootstrap username is created only if absent. It is a USER account, and password changes in the environment do not reset existing credentials. Registration and credential administration require a future authenticated design.

## Deterministic execution

Run creation locks the owned workflow, resolves an explicit immutable version, validates executable semantics, and persists the run and ordered step plan in one transaction. The unique `(owner_id, workflow_id, idempotency_key)` constraint and workflow lock serialize duplicate starts. Replays also lock the run when building details. Subsequent workflow edits or archives do not alter the bound version or plan.

The scheduled worker reads at most 20 PENDING/RUNNING IDs and calls the transactional service once per ID. Each call locks the run and advances one pure local action or a bounded local planner/tool batch. The run checkpoint, step outcome, proposal/decision, usage and audit events commit together. There is no persisted in-flight step state: a process death before commit rolls everything back; after commit the next worker skips that completed step. Concurrent workers and cancellation share the run lock. Trace reads use a repeatable-read transaction to keep run, step and event snapshots consistent.

The only handlers receive input, classify by local rules, search a bounded built-in catalog, and record a local result. Issue text is data; it cannot choose a URL, shell command, tool, owner, or approval. No external effects occur under database locks. Handler failures terminate the run and cancel remaining steps. Database transaction failures preserve the previous checkpoint for the next poll.

`audit_events` contains append-only lifecycle and step events with a unique per-run sequence. Actor ownership is available through the immutable run owner. It is not a complete security audit system yet. Submitted issue content and checkpoint snapshots are stored in run/step records and exposed only to their owner.

The process-restart script tests an abrupt Java process death against PostgreSQL and fresh-process recovery. Transaction rollback and worker races are also tested against both H2 and PostgreSQL. These guarantees apply to the current local handlers; a database transaction cannot provide exactly-once external execution.

V2/V3 preserve existing workflow and run records. V3 adds run mode/usage, optional agent settings and proposal/decision metadata. Old runs default to DETERMINISTIC with zero planner/tool usage. A database constraint requires each saved step to have exactly one of operation or agent settings. Disable the worker to stop automatic advancement while preserving history. A rollback artifact must retain applied migration metadata; older artifacts have not been tested against the newer schema. Schema rollback requires a database backup/restore, rather than automatically discarding history.

## Planner and tool boundary

An AI_STEP freezes a goal, workflow tool allowlist and per-step tool budget in its version and execution plan. The only planner implementation is local and deterministic. It returns an untrusted JSON proposal, with no principal, executor, approval handle or credential in its request.

ProposalPolicy rejects oversized/malformed output, duplicate keys, trailing JSON and unknown fields. It validates the complete batch before any handler runs. ToolRegistry then checks registration, workflow permissions, strict typed arguments, current persisted role and server-derived issue/category context. Accepted arguments are serialized into immutable strings and revalidated before dispatch to their registered local operation. Rejected raw proposals are discarded; the trace retains a safe denial code. Code owns the two tool definitions; catalog persistence and administrative registration are future work.

Runs containing agent steps have limits of five committed planner calls, ten local tool calls, three calls per proposal (also constrained by the step budget), 16 KiB per proposal, and 120 seconds from RUNNING. The worker checks elapsed budget before each step. Counters, approved proposals and tool checkpoints survive process restart and roll back together on transaction abort. These limits do not imply live-provider token accounting or interruption of remote calls; no provider or remote tool exists in this slice.

Policy APPROVED means Java validation passed; it is not human approval. Only local read-only tools can be proposed. Approval nodes still fail closed. Model/provider failures are tested through controlled planner faults and return PLANNER_FAILURE without internal details.

## Runtime extension

The next slice adds a credential-configured live provider and token/provider deadlines. The current pure planner must not simply be replaced by a network-calling bean under the run lock: remote planning needs persisted intent/result stages outside the transaction, followed by Java revalidation. Sensitive effects then require a durable human approval bound to the exact proposal. Remote tools need their own idempotency and reconciliation for uncertain outcomes.

Live providers, external tools, approval decisions, branching semantics, and action retries remain future work. Persisting AI/action node labels has no side effect.
