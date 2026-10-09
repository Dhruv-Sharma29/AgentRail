---
name: workflow-engine-reliability
description: Implement and review AgentFlow durable run states, retries, idempotency, checkpoints, cancellation, and approval pause and resume.
---

# workflow-engine-reliability

Specify permitted state transitions before implementing the slice. Runs reference immutable graph versions. Validate IDs, connections, reachability, node types, and limits before execution.

- Persist run and node transitions with UTC timestamps, attempts, safe errors, and audit events. Define crash recovery for intermediate states.
- Persist approval pauses bound to run, node, exact proposal, owner, and required role. Rejected, cancelled, and expired requests cannot authorize effects.
- Serialize competing transitions with optimistic locking, a narrow row lock, or an atomic conditional update. Test duplicate and conflicting decisions.
- Scope idempotency keys to owner and workflow and bind them to the payload. Reuse returns the same result; differing payloads conflict. Enforce uniqueness in the database.
- External tools need their own idempotency and reconciliation. Database transactions do not provide exactly-once external execution; document delivery semantics.
- Retry only transient failures with bounded attempts, backoff, and jitter. Validation errors and denials are terminal. Reconcile ambiguous remote outcomes before retry.
- Timeouts do not prove a remote action stopped. Cancellation must prevent later nodes and define in-flight behavior.

Use fake clocks and controlled faults for recovery tests. Verify restart and concurrency on PostgreSQL when introduced. Add Redis, queues, or another workflow engine only when the implemented slice requires them.
