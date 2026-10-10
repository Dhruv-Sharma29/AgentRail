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

Slices 1–3 implement the deterministic proposal boundary. Slice 4 implements opt-in Spring AI/OpenAI planning with durable request/result stages, usage reservations, deadlines and fail-closed recovery. The Gemini extension reuses the bounded manual-tool Spring AI boundary through Google's OpenAI-compatible endpoint. Offline and PostgreSQL contracts pass, and the live Gemini demo succeeded with two planner calls, two tool calls and fifteen audit events. OpenAI live completion remains blocked by API quota. Human approvals and local reliability are implemented in Phase 4; external ticket effects require a separate tool increment.


## Phase 4 slices

1. Durable approvals: bounded configured APPROVAL before RECORD_RESULT; immutable payload/digest and owner/role binding; owner inbox/read/decision API; duplicate/conflicting decisions, rejection, expiry and cancellation tested.
2. Resume and recovery: row-lock serialization, exact input/context revalidation, current role checks, persisted excluded approval time, rollback and PostgreSQL concurrency tests; real SIGKILL/fresh-process test while waiting for a decision.
3. Bounded local retries: explicit pure ACTION allowlist and Java-classified transient failures; three attempts, persisted backoff/jitter, audits, deadline/cancellation/approval checks; controlled-fault contracts on H2 and PostgreSQL. Agent/provider calls and uncertain remote effects are never blindly retried.
4. Review and demo: migration of populated V4 databases preserves history; full verification; interactive approval demo and API/runtime documentation.

Scope: the complete support flow now reaches a human checkpoint and records an approved local result. External ticket creation, independent approver roles, remote-effect idempotency/reconciliation and branching are separate increments. Phase 5 can build its editor, traces and approval inbox against these stable APIs.


## Phase 5 slices — implemented

1. Persist optional bounded editor coordinates in immutable workflow versions; strict validation and legacy definitions remain compatible. No schema migration or execution permission is added.
2. Build a React/TypeScript/Tailwind workspace with cookie sessions, CSRF rotation, owner-scoped workflow CRUD, version history and a controlled React Flow editor. Keep transport, graph conversion, resource lifecycle and screens in separate modules.
3. Connect explicit saved-version starts, retained request keys, step/audit traces, terminal-aware polling and cancellation. Add the exact-payload approval inbox and registered-tool inspection.
4. Verify with API/graph unit tests, production compilation and a real deterministic backend in Chromium. Exercise conflict preservation, literal hostile text, lost start responses, keyboard editing, modal focus, logout and session expiry. Document startup and add frontend CI.

The next increment is Phase 6: a same-origin frontend/API container deployment, PostgreSQL Compose verification, operational configuration, security/dependency review and a recorded demo. This phase does not introduce external ticket tools, branching, independent approvers or a production ingress.
