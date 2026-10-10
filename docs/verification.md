# Verification and review

Verified locally on 2026-10-10 with Oracle JDK 25.0.1; compilation targets Java 21. CI is configured for Temurin 21. The original foundation's PostgreSQL gate also passed in the user's supplied terminal log: 27 H2 tests plus 13 PostgreSQL tests.

## Successful checks

- All 11 installed/project skills passed the skill-creator validator; referenced Markdown resources exist.
- `./mvnw -o verify`: 78 tests passed, no failures, errors or skips. Includes 51 H2/unit tests and 27 PostgreSQL contract tests, and builds the executable jar. PostgreSQL tests exercise Flyway V1/V2, schema validation, ownership, immutable versions, request idempotency, concurrent starts/workers, cancellation, failure termination and checkpoint rollback.
- `python3 scripts/restart-test.py`: passed against an isolated PostgreSQL container. The script created a run, waited for its first committed step, sent SIGKILL to its own Java child, started a fresh Java process, and verified SUCCEEDED, one attempt per step and exactly seven ordered audit events.
- The foundation's real HTTP smoke test passed with session cookies, actual password login and CSRF tokens. The new restart test exercises the same login mechanism and the execution demo's real HTTP workflow/start/poll/idempotency path.
- `python3 scripts/execution-demo.py --base-url <isolated-local-port>` passed against a fresh dev-profile jar and temporary H2 database. All four steps succeeded, duplicate start reused the run, and seven audit events were reported.
- Maven's pinned distribution was verified against SHA-512 and its SHA-256 recorded in the wrapper.

TDD evidence: foundation graph tests recorded 12 initial failures and HTTP tests eight contract failures. Execution API tests recorded six failures before operation support and endpoints existed, followed by endpoint 404 failures. Execution policy and handler/worker tests failed before their classes/methods existed. A trace regression failed because the trigger's input omitted the submitted subject; it passes after recording the issue as trigger input.

The local test environment uses an explicit Mockito agent because dynamic attachment failed under Java 25. PostgreSQL verification requires Docker socket access beyond the filesystem sandbox; the authorized run passed. Docker Desktop subsequently stopped before the restart script could connect. Reopening Docker restored the socket and the restart check passed. No integration checks silently skip missing Docker.

## Separate review pass

Reviewed tests first, then correctness, readability, module boundaries, security, query bounds and recovery behavior using code-review-and-quality and agent-security-review.

- Ownership derives from the persisted security principal. Run starts, reads, cancellation and history are owner-scoped; missing/unowned resources return 404. Unknown ownership/input fields fail validation. Mutations retain CSRF protection.
- A run's graph version, input and execution plan are immutable through the API. Labels cannot select operations. AI/approval/condition/branching nodes are refused at start; approval nodes cannot be silently bypassed.
- Idempotency keys bind owner, workflow, exact input and explicit version. A workflow lock and database uniqueness constraint prevent duplicate starts. A run lock serializes worker advances, cancellation and replay snapshots. Read traces use a repeatable-read transaction.
- Local handlers have no shell, SQL, arbitrary filesystem, URL, model or external ticket capability. Issue text remains data. Graphs, issue inputs, history pages and worker batches are bounded.
- Step outcomes, run checkpoints and audit records commit atomically. Rollback leaves the prior checkpoint intact; restart discovers pending/running work. Cancelled/failed runs cannot advance, and terminal cancellation conflicts. No claim of exactly-once external execution is made.
- Audit events omit issue text and credentials. Run/step inputs and outputs contain issue content and are restricted to their owner. Handler error codes omit exception details. The initial audit scope covers execution, not all security activity.
- Existing schema and version definitions remain compatible. Optional typed operations preserve old saved graphs; unconfigured actions cannot execute. No dependencies or model-provider configuration were added.
- Structural discovery used codebase-memory and cited-path coverage checks, with source inspection for incomplete Java call relationships and new/changed files. Graph coverage remains a best-effort signal.

## Limits and follow-up work

The current engine supports bounded local actions in one linear chain. Live AI/tool proposals, external ticket creation, approval pauses, branching, tool retries, deadlines and remote-effect reconciliation are future phases. Authentication/administration audits, registration, password rotation, login throttling, retention/encryption policy and production ingress remain future work.

Container image builds, a full running Compose deployment and remote deployment have not been verified. The process restart test does verify the application jar against PostgreSQL; it does not verify the production container image. CI configuration is committed, but a hosted CI run is not claimed.
