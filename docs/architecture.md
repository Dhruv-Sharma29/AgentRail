# Foundation architecture

## Implemented modules

| Module | Responsibility | Boundary |
| --- | --- | --- |
| auth | Persisted users, BCrypt passwords, session login, logout, CSRF | Spring Security establishes the principal; requests cannot select ownership |
| workflow | DAG validation, owner-scoped CRUD, immutable version history | Controllers pass the principal to transactional services; repositories scope object queries to owner |
| common | Safe controller error responses | Malformed requests are 400, missing/unowned resources 404, stale writes 409 |

The remaining execution, agent, tools, approvals, and audit packages are planned and have no placeholder runtime implementations.

## Versioning and transactions

A workflow is a mutable metadata record with owner, current version, archive state, and an optimistic revision counter. Each successful create/update writes a separate version containing the complete definition and metadata snapshot. The application exposes no mutation endpoint for historical versions; version columns are marked non-updatable in JPA.

Creation writes workflow and initial version in one transaction. Update locks the owned workflow row, checks expectedVersion, validates the graph, changes metadata, and writes the next version in one transaction. The `(workflow_id, number)` database constraint prevents duplicate version numbers. Concurrent edit tests require one success and one conflict. Archive shares the same row lock and preserves versions.

Lists use bounded pagination. Details fetch only the current version, while history pages definitions oldest first. There are no lazy relationships serialized as API output and no network calls inside these transactions.

## Database and deployment

Flyway creates users, workflows, and workflow_versions. Hibernate validates the schema. The explicit dev profile uses file-backed H2 for standalone local work. The default profile uses PostgreSQL with required environment configuration. Local Compose binds exposed ports to loopback; Secure session cookies remain enabled in the default application configuration.

The bootstrap username is created only if absent. It is a USER account, and password changes in the environment do not reset existing credentials. Registration and credential administration require a future authenticated design.

## Runtime extension

The next slice binds a run to a workflow version and persists its node states. A planner returns structured proposals; a Java policy boundary authorizes approved tools and validates arguments. Sensitive effects pause with an immutable proposal before execution. Remote calls must occur outside database locks, with durable intents, tool-specific idempotency, and reconciliation for uncertain outcomes.

The foundation does not run graphs, call providers, execute tools, make approval decisions, retry actions, or write execution audits. Persisting AI/action node labels has no side effect.
