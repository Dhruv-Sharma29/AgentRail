# Verification and review

Verified locally on 2026-10-10 with Oracle JDK 25.0.1; compilation targets Java 21. CI is configured for Temurin 21. The original foundation's PostgreSQL gate also passed in the user's supplied terminal log: 27 H2 tests plus 13 PostgreSQL tests.

## Successful checks

- All 11 installed/project skills passed the skill-creator validator; referenced Markdown resources exist.
- `./mvnw -o verify`: 127 tests passed, no failures, errors or skips. Includes 85 H2/unit tests and 42 PostgreSQL contract tests, and builds the executable jar. PostgreSQL tests exercise Flyway V1/V2/V3, ownership, immutable versions, request idempotency, concurrent workers, proposal policy, revoked roles, planner/tool/elapsed budgets, cancellation, failures and atomic checkpoint/usage rollback.
- `python3 scripts/restart-test.py`: passed against an isolated PostgreSQL container. The script created a run, waited for its first committed step, sent SIGKILL to its own Java child, started a fresh Java process, and verified SUCCEEDED, one attempt per step and exactly seven ordered audit events.
- `python3 scripts/restart-test.py --agent`: passed after the first agent step committed its proposal and one planner/tool call. After SIGKILL and fresh-process recovery, the original proposal remained recorded, the run succeeded with two planner/tool calls, and all thirteen audit events were preserved without duplicate steps.
- The foundation's real HTTP smoke test passed with session cookies, actual password login and CSRF tokens. The new restart test exercises the same login mechanism and the execution demo's real HTTP workflow/start/poll/idempotency path.
- `python3 scripts/execution-demo.py --base-url <isolated-local-port>` passed against a fresh dev-profile jar and temporary H2 database. All four steps succeeded, duplicate start reused the run, and seven audit events were reported.
- `python3 scripts/execution-demo.py --agent --base-url <isolated-local-port>` passed against a fresh dev-profile jar and temporary H2 database. Both proposals were approved by Java policy; two planner calls, two tool calls and thirteen audit events were recorded. The user's server on port 8080 was left running.
- Maven's pinned distribution was verified against SHA-512 and its SHA-256 recorded in the wrapper.

TDD evidence: foundation and execution contract tests failed before implementation; the trigger-input regression failed before its fix. Planner policy tests failed before the registry/policy existed, and agent contract tests failed before the planner and persisted settings were connected. Regressions caught a run mode written after immutable insertion and an elapsed check that omitted the final action; both now pass. A mock restubbing fault in the test harness was corrected separately from runtime behavior.

The local test environment uses an explicit Mockito agent because dynamic attachment failed under Java 25. PostgreSQL verification requires Docker socket access beyond the filesystem sandbox; the authorized run passed. Docker Desktop subsequently stopped before the restart script could connect. Reopening Docker restored the socket and the restart check passed. No integration checks silently skip missing Docker.

## Separate review pass

Reviewed tests first, then correctness, readability, module boundaries, security, query bounds and recovery behavior using code-review-and-quality and agent-security-review.

- Ownership derives from the persisted security principal. Run starts, reads, cancellation and history are owner-scoped; missing/unowned resources return 404. Unknown ownership/input fields fail validation. Mutations retain CSRF protection.
- A run's graph version, input and execution plan are immutable through the API. Agent settings freeze the allowed tools and bounded goal. Unconfigured AI/approval/condition/branching nodes are refused at start; approval nodes cannot be silently bypassed.
- Idempotency keys bind owner, workflow, exact input and explicit version. A workflow lock and database uniqueness constraint prevent duplicate starts. A run lock serializes worker advances, cancellation and replay snapshots. Read traces use a repeatable-read transaction.
- Local handlers have no shell, SQL, arbitrary filesystem, URL, model or external ticket capability. Issue text remains data. Proposal policy checks the whole batch before effects, registered names, workflow permissions, strict typed arguments, persisted role and server issue/category context. Frozen arguments are revalidated before dispatch. Graphs, inputs, proposal bytes, batches, usage and elapsed budgets are bounded.
- Step outcomes, run checkpoints and audit records commit atomically. Rollback leaves the prior checkpoint intact; restart discovers pending/running work. Cancelled/failed runs cannot advance, and terminal cancellation conflicts. No claim of exactly-once external execution is made.
- Audit events omit issue text and credentials. Run/step inputs, outputs and accepted proposals contain issue content and are restricted to their owner. Rejected raw proposals are discarded. Handler/planner error codes omit exception details. The initial audit scope covers execution, not all security activity; policy APPROVED is not a human approval.
- Existing schema/version definitions remain compatible. Optional agent settings preserve old saved graphs; V3 defaults old runs to DETERMINISTIC with zero planner/tool usage. Unconfigured nodes cannot execute. No dependencies or model-provider configuration were added.
- Structural discovery used codebase-memory and cited-path coverage checks, with source inspection for incomplete Java call relationships and new/changed files. The refreshed index reported partial SQL parsing on V3 lines 2–3; the migration was read directly and exercised by PostgreSQL tests. Graph coverage remains a best-effort signal.

## Limits and follow-up work

The current engine supports bounded local actions and deterministic planner proposals in one linear chain. Live providers and their token/deadline accounting, external ticket creation, human approval pauses, branching, tool retries and remote-effect reconciliation remain future work. A live provider must use intent/result stages outside database locks, rather than replacing the pure planner bean inside the current transaction. Tool catalog persistence/administration, authentication/administration audits, registration, password rotation, login throttling, retention/encryption policy and production ingress remain future work.

Container image builds, a full running Compose deployment and remote deployment have not been verified. The process restart test does verify the application jar against PostgreSQL; it does not verify the production container image. CI configuration is committed, but a hosted CI run is not claimed.
