# Phase 4: approvals and reliability

First slice: a configured APPROVAL node immediately precedes the local RECORD_RESULT action. Its timeoutSeconds is 60–86,400. The engine freezes the target node/operation, original issue and checkpoint as a JSON action payload, saves its SHA-256 digest and required USER role, and pauses both node and run. Only the owner can review and decide; independent approver roles and external ticket tools remain separate increments.

Acceptance criteria:

- PENDING → RUNNING → WAITING_FOR_APPROVAL → RUNNING → SUCCEEDED; rejected/expired approvals terminate FAILED, cancellation terminates CANCELLED.
- The authenticated owner reviews an immutable payload and submits APPROVE or REJECT with its exact digest. No request can replace action arguments, ownership or role.
- Duplicate identical decisions are idempotent; conflicting, expired and cancelled decisions cannot authorize an action. A run row lock serializes worker, decision and cancellation transitions.
- Worker execution rechecks the current role and exact frozen input/checkpoint/target. Changed payloads or context fail closed.
- Approval waiting time is durably excluded from the 120-second active execution budget for all run modes. A separate approval deadline is enforced at the decision endpoint and by the worker.
- Pause creation and decisions commit with their audit events and checkpoints. PostgreSQL races, rollback and real process restart are verified.
- Provider requests and uncertain remote outcomes are never retried. Pure ACTION retries accept only Java-classified transient failures on an explicit operation allowlist: three attempts, 2/4-second persisted backoff plus 0–500 ms jitter. Success clears the schedule; cancellation/denials/deadlines prevent later execution. Agent batches are terminal on tool failure.
