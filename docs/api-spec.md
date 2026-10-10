# AgentFlow API

Base URL: `http://127.0.0.1:8080`. All routes except CSRF, login, and health require a session. All mutations, including login and logout, require a CSRF token. JSON rejects unknown properties.

## Authentication

1. `GET /api/auth/csrf` returns `{token, headerName, parameterName}` and establishes a session. Preserve the session cookie.
2. `POST /api/auth/login` accepts URL-encoded `username`, `password`, and the returned CSRF parameter. Success is 204; incorrect credentials are 401.
3. Fetch a fresh CSRF token after login; authentication rotates it. Send it under the returned header name on mutations.
4. `GET /api/auth/me` returns `{username, roles}`.
5. `POST /api/auth/logout` returns 204 and invalidates the session.

Cookies are HttpOnly, SameSite=Lax, and Secure by default. The loopback-only dev profile and local Compose configuration turn Secure off for local HTTP. Production requires HTTPS and Secure cookies. No browser token storage or CORS policy is needed until the frontend exists; the planned client will use the same origin or a development proxy.

## Workflows

| Method | Route | Behavior |
| --- | --- | --- |
| POST | `/api/workflows` | Create workflow and version 1; 201 with Location and details |
| GET | `/api/workflows?page=0&size=20` | Owner's active workflows; paginated summaries |
| GET | `/api/workflows/{id}` | Owner-only details, including current definition and archive state |
| PUT | `/api/workflows/{id}` | Create next immutable version; requires expectedVersion |
| DELETE | `/api/workflows/{id}` | Idempotently archive; 204; preserves history |
| GET | `/api/workflows/{id}/versions?page=0&size=20` | Owner-only immutable definitions and metadata, oldest first |

Pages return `{items, total, page, size}`. Page must be nonnegative; size is 1–100. Unowned and missing objects both return 404. Archived workflows remain inspectable by their owner and cannot be updated; active listings exclude them. Stale updates return 409. Updates and archive operations lock only the owned workflow row inside a transaction.

Create body:

```json
{
  "name": "Support intake",
  "description": "Triage incoming issues",
  "definition": {
    "nodes": [
      {"id": "intake", "type": "TRIGGER", "label": "Receive issue"},
      {"id": "ticket", "type": "ACTION", "label": "Create ticket"}
    ],
    "edges": [{"source": "intake", "target": "ticket"}]
  }
}
```

An update uses the same body plus `"expectedVersion": 1`. Names are required, limited to 120 characters, and trimmed. Description is required and limited to 2,000 characters. Definitions support 1–50 nodes and at most 100 edges; node IDs match `[a-zA-Z0-9_-]{1,64}` and labels are required, at most 120 characters. Node types: TRIGGER, AI_STEP, CONDITION, APPROVAL, ACTION.

Saving validates structure: exactly one trigger, unique IDs, existing edge endpoints, no duplicate/self edges, no cycles, and every node reachable from the trigger. Saving a graph has no execution effect. Nodes can optionally include an `operation` enum; existing definitions without it remain readable. The registered local operations are `RECEIVE_INPUT`, `CLASSIFY_ISSUE`, `SEARCH_DOCUMENTATION`, and `RECORD_RESULT`. An unknown operation is a malformed request.

Validation errors are 400; conflicting changes are 409. Controller errors return `{status, message, timestamp}`. Authentication/CSRF errors are 401/403 from Spring Security. Errors do not expose stack traces. `GET /actuator/health` exposes health without internal details.

## Workflow runs

| Method | Route | Behavior |
| --- | --- | --- |
| POST | `/api/workflows/{id}/runs` | Start a saved version; 202 with Location and run details |
| GET | `/api/workflows/{id}/runs?page=0&size=20` | Owner-only paginated run summaries, newest first |
| GET | `/api/runs/{id}` | Owner-only run, ordered steps, checkpoint and ordered audit events |
| POST | `/api/runs/{id}/cancel` | Cancel pending/running work; 200 with details |

Start body:

```json
{
  "versionNumber": 1,
  "idempotencyKey": "support-issue-001",
  "input": {"subject": "Cannot log in", "body": "Password reset failed"}
}
```

`versionNumber` is required and positive. `idempotencyKey` is required and matches `[a-zA-Z0-9_-]{1,100}`. Subject and body are nonblank, limited to 200 and 4,000 characters respectively. Unknown input fields fail validation. Keys are scoped to the authenticated owner and workflow and permanently bound to the requested version and exact input. Replaying the same request returns the same run with 202, including after completion or archive. Changed input or version returns 409. A new run of an archived workflow returns 409; missing/unowned workflow, version, or run returns 404.

Execution requires a single chain of TRIGGER/ACTION nodes. The trigger receives input and can omit its operation or specify `RECEIVE_INPUT`. Every action must explicitly specify one of the other three operations; labels do not select behavior. Unsupported AI, approval, condition, branching, and missing action operations return 400 before run creation. See [the executable example](../examples/support-intake.json).

Details contain `id`, `workflowId`, `versionId`, `versionNumber`, `mode: "DETERMINISTIC"`, `status`, lifecycle timestamps, `errorCode`, submitted `input`, the current `context`, ordered `steps`, and `events`. Each step contains node ID, ordinal, operation, status, attempts, input/output, timestamps and safe error code. The trigger's input is the submitted issue; later steps receive the previous checkpoint. Outputs are full checkpoint snapshots. Audit events have sequence, node ID, event type and UTC timestamp; they contain no issue text or credentials.

Runs transition `PENDING → RUNNING → SUCCEEDED/FAILED`, or `PENDING/RUNNING → CANCELLED`. Each step is atomically recorded as SUCCEEDED or FAILED; unexecuted steps remain PENDING or become CANCELLED. Cancellation preserves completed steps and prevents future ones. Repeated cancellation is idempotent; cancellation of a succeeded/failed run returns 409. A short local action already holding the run lock finishes before cancellation can acquire that lock.

Missing prerequisite context terminates the run with codes such as `MISSING_CLASSIFICATION` or `MISSING_SUPPORT_CONTEXT`; subsequent steps are cancelled without attempts. Unexpected handler errors use `INTERNAL_STEP_ERROR`, without exception details. These failures are not automatically retried. A worker rediscovers PENDING/RUNNING runs after restart and continues from the first pending step. No public advance/resume endpoint exists.

Worker configuration: `agentflow.execution.worker-enabled` (default true), `agentflow.execution.poll-delay-ms` (default 1000). Tests disable scheduling and control transaction boundaries explicitly. The worker processes at most 20 runnable IDs per poll and one bounded local step per run.
