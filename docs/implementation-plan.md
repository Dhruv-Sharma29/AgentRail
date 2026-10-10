# Implementation plan

Each slice must compile, pass its behavior tests, have a reviewed API contract, and document its limits. Add tests before implementation. Perform correctness, security, and simplification reviews before committing a phase.

| Phase | Deliverable | Acceptance criteria | Depends on |
| --- | --- | --- | --- |
| 1 | Authenticated workflow foundation | PostgreSQL migrations; session auth and CSRF; owner-scoped CRUD; immutable versions; malformed/cyclic/disconnected graphs rejected; tests and CI | Specification |
| 2 | Deterministic execution | Saved version bound to run; sequential execution; persisted node states; failures and cancellation recorded; restart strategy tested | 1 |
| 3 | Planner and tool boundary | Deterministic planner first; registered tool allowlist; strict argument validation; bounded tool/model budgets; provider credentials configured before live integration | 2 |
| 4 | Approvals and reliability | Durable pause; exact proposal binding; role checks; duplicate/concurrent decisions safe; classified bounded retries; timeout and ambiguous effect handling tested | 3 |
| 5 | Visual frontend | React Flow editor; workflow CRUD; run traces; approval inbox; accessible states; critical demo covered by browser tests | 1–4 |
| 6 | Delivery | PostgreSQL integration and restart tests; container deployment; CI; operational docs; threat review; recorded demo | 1–5 |

## Phase 1 slices

1. Pin the Java/Spring build and wrapper; add configuration, migrations, and schema validation.
2. Test graph validation for duplicate IDs, dangling/duplicate edges, cycles, disconnected nodes, and invalid trigger counts; implement a bounded DAG validator.
3. Test workflow creation, owner isolation, version history, stale updates, and archive behavior; implement DTOs, persistence, and services.
4. Test unauthenticated requests, password login, CSRF enforcement, and invalid/unknown fields; implement session security and the HTTP API.
5. Verify local startup and the authenticated CRUD path; add PostgreSQL Testcontainers coverage and Docker/CI configuration. Report any checks requiring unavailable infrastructure.

The first increment ends at workflow management. It does not claim to execute tools or call a model. The next increment implements the deterministic runtime against this API and versioning contract.

## Phase 2 slices

1. Extend definitions with optional typed operations and test executable semantics: linear chain only, explicit local actions, no AI/condition/approval bypass.
2. Add immutable-version run binding, bounded issue input, scoped request idempotency, run history, ownership and CSRF checks.
3. Persist ordered steps, checkpoints and lifecycle audit events; implement pure local support handlers.
4. Advance one step per transaction through a bounded polling worker; test rollback, competing workers, failure termination and cancellation.
5. Verify the same contracts against PostgreSQL and abrupt Java process restart; add executable demo and restart scripts, CI and runtime documentation.

This slice records a local support result. Remote ticket creation, live classification, approval pauses and retries belong to later phases. Phase 3 introduces proposal validation against a Java tool registry before adding a provider.

## Phase 3 slices

1. Registered tools and proposal policy: strict envelope/argument validation, workflow allowlist, server role/context checks, response and batch bounds; code-controlled read-only catalog.
2. Durable agent steps: immutable goal/allowlist/budget settings, deterministic proposal adapter, persisted accepted proposals/denials, usage and tool audit outcomes; atomic rollback and cancellation.
3. Run budgets and recovery: planner/tool/elapsed limits, role revocation and competing-worker tests, PostgreSQL verification, process restart after an approved proposal, runnable agent demo.
4. Live provider integration: resolve credentials before provider work, verify official Spring AI/provider contracts, persist planning intents and results outside run locks, enforce token/deadline budgets, and keep paid tests opt-in.

Slices 1–3 implement the deterministic proposal boundary. Slice 4 implements opt-in Spring AI/OpenAI planning with durable request/result stages, usage reservations, deadlines and fail-closed recovery. Offline and PostgreSQL contracts pass; the live smoke request encountered HTTP 429, leaving successful live completion pending provider limit resolution. Human approvals and external ticket effects remain Phase 4.
