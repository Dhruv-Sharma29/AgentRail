# 04 — API reference

This is the implemented REST contract. Routes use the same origin as the web app: Vite proxies them in development and Nginx proxies them in Compose. The direct development backend URL below is useful for API clients.

Base URL: `http://127.0.0.1:8080`. All routes except CSRF, login, and health require a session. All mutations, including login and logout, require a CSRF token. JSON rejects unknown properties.

## Authentication

1. `GET /api/auth/csrf` returns `{token, headerName, parameterName}` and establishes a session. Preserve the session cookie.
2. `POST /api/auth/login` accepts URL-encoded `username`, `password`, and the returned CSRF parameter. Success is 204; incorrect credentials are 401.
3. Fetch a fresh CSRF token after login; authentication rotates it. Send it under the returned header name on mutations.
4. `GET /api/auth/me` returns `{username, roles}`.
5. `POST /api/auth/logout` returns 204 and invalidates the session.

Cookies are HttpOnly, SameSite=Lax, and Secure by default. The loopback-only dev profile and local Compose configuration turn Secure off for local HTTP. Production requires HTTPS and Secure cookies. The React client uses same-origin requests through the development or container proxy. Session credentials are not stored in browser local storage.

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

Create body (a runnable local workflow):

```json
{
  "name": "Deterministic support intake",
  "description": "Local classification, documentation lookup and persisted result. No model or external ticket service.",
  "definition": {
    "nodes": [
      {
        "id": "receive",
        "type": "TRIGGER",
        "label": "Receive issue"
      },
      {
        "id": "classify",
        "type": "ACTION",
        "label": "Classify issue",
        "operation": "CLASSIFY_ISSUE"
      },
      {
        "id": "search",
        "type": "ACTION",
        "label": "Find documentation",
        "operation": "SEARCH_DOCUMENTATION"
      },
      {
        "id": "record",
        "type": "ACTION",
        "label": "Record result",
        "operation": "RECORD_RESULT"
      }
    ],
    "edges": [
      {
        "source": "receive",
        "target": "classify"
      },
      {
        "source": "classify",
        "target": "search"
      },
      {
        "source": "search",
        "target": "record"
      }
    ]
  }
}
```

An update uses the same body plus `"expectedVersion": 1`. Names are required, limited to 120 characters, and trimmed. Description is required and limited to 2,000 characters. Definitions support 1–50 nodes and at most 100 edges; node IDs match `[a-zA-Z0-9_-]{1,64}` and labels are required, at most 120 characters. Node types: TRIGGER, AI_STEP, CONDITION, APPROVAL, ACTION.

Saving validates structure: exactly one trigger, unique IDs, existing edge endpoints, no duplicate/self edges, no cycles, and every node reachable from the trigger. Saving a graph has no execution effect. Nodes can optionally include an `operation` enum; existing definitions without it remain readable. The registered local operations are `RECEIVE_INPUT`, `CLASSIFY_ISSUE`, `SEARCH_DOCUMENTATION`, and `RECORD_RESULT`. The external operation `CREATE_TICKET` is also recognized, with the additional approval and destination requirements described below. An unknown operation is a malformed request. Nodes may include `position: {x, y}` for the visual editor; both coordinates are required when present and bounded to −10,000 through 10,000. Positions are optional versioned metadata and have no execution meaning.

Validation errors are 400; conflicting changes are 409. Controller errors return `{status, message, timestamp}`. Authentication/CSRF errors are 401/403 from Spring Security. Errors do not expose stack traces. `GET /actuator/health` exposes health without internal details.

## Workflow runs

| Method | Route | Behavior |
| --- | --- | --- |
| POST | `/api/workflows/{id}/runs` | Start a saved version; 202 with Location and run details |
| GET | `/api/workflows/{id}/runs?page=0&size=20` | Owner-only paginated run summaries, newest first |
| GET | `/api/runs/{id}` | Owner-only run, ordered steps, checkpoint and ordered audit events |
| POST | `/api/runs/{id}/cancel` | Cancel pending/running/waiting work; 200 with details |

Start body:

```json
{
  "versionNumber": 1,
  "idempotencyKey": "support-issue-001",
  "input": {"subject": "Cannot log in", "body": "Password reset failed"}
}
```

`versionNumber` is required and positive. `idempotencyKey` is required and matches `[a-zA-Z0-9_-]{1,100}`. Subject and body are nonblank, limited to 200 and 4,000 characters respectively. Unknown input fields fail validation. Keys are scoped to the authenticated owner and workflow and permanently bound to the requested version and exact input. Replaying the same request returns the same run with 202, including after completion or archive. Changed input or version returns 409. A new run of an archived workflow returns 409; missing/unowned workflow, version, or run returns 404.

Execution requires a single chain of TRIGGER/ACTION/configured AI_STEP/APPROVAL nodes. The trigger receives input and can omit its operation or specify `RECEIVE_INPUT`. Every action must explicitly specify a supported action operation; labels do not select behavior. `CREATE_TICKET` additionally requires the final-step approval contract below. Condition, branching, and unconfigured action/agent/approval nodes return 400 before run creation. See [the action example](../examples/support-intake.json) and [the agent example](../examples/agent-support-intake.json).

Details contain `id`, `workflowId`, `versionId`, `versionNumber`, `mode` (DETERMINISTIC, DETERMINISTIC_PLANNER, OPENAI_PLANNER or GEMINI_PLANNER), `status`, lifecycle timestamps, `errorCode`, submitted `input`, current `context`, ordered `steps`, `events`, `plannerCalls`, `toolCalls`, `reservedPlannerTokens`, `plannerTokens`, `approvals` and `tickets`. Each step contains node ID, ordinal, operation, status, attempts, input/output, timestamps, safe error code and nullable planner/proposal/policy metadata, planningState, planningDeadline and nextAttemptAt. The trigger's input is the submitted issue; later steps receive the previous checkpoint. Outputs are full checkpoint snapshots. Audit events have sequence, node ID, event type, UTC timestamp, and optional tool name/decision code; they contain no issue text or credentials.

Runs transition `PENDING → RUNNING → SUCCEEDED/FAILED`, may pause at `WAITING_FOR_APPROVAL` and resume RUNNING, or cancel from any nonterminal state. Each step is atomically recorded as SUCCEEDED or FAILED; unexecuted steps remain PENDING or become CANCELLED. Cancellation preserves completed steps and prevents future ones. Repeated cancellation is idempotent; cancellation of a succeeded/failed run returns 409. A short local action already holding the run lock finishes before cancellation can acquire that lock.

Missing prerequisite context terminates the run with codes such as `MISSING_CLASSIFICATION` or `MISSING_SUPPORT_CONTEXT`; subsequent steps are cancelled without attempts. Unexpected handler errors use `INTERNAL_STEP_ERROR`, without exception details. These failures are not automatically retried. Explicitly classified pure local transient failures follow the bounded retry contract below. A worker rediscovers PENDING/RUNNING runs after restart and continues from the first pending step. No public advance/resume endpoint exists.

Worker configuration: `agentflow.execution.worker-enabled` (default true), `agentflow.execution.poll-delay-ms` (default 1000). Tests disable scheduling and control transaction boundaries explicitly. The worker processes at most 20 runnable IDs per poll and one bounded step or planning dispatch per run.

## Agent steps and tools

An agent node omits `operation` and supplies these settings:

```json
{
  "id": "classify", "type": "AI_STEP", "label": "Propose classification",
  "agent": {"goal": "CLASSIFY_ISSUE", "allowedTools": ["classify_issue"], "maxToolCalls": 1}
}
```

Goals are CLASSIFY_ISSUE or FIND_DOCUMENTATION. The allowlist contains one or two distinct registered names, and maxToolCalls is 1–3. Registration and executable semantics are checked again at run start. Agent settings cannot be attached to trigger/action nodes, and condition nodes are unsupported. Existing saved definitions remain readable with null agent settings.

`GET /api/tools` requires authentication and returns the code-controlled catalog: two READ_ONLY tools (`classify_issue`, `search_documentation`) and the EXTERNAL_WRITE action descriptor (`create_ticket`). Entries include name, description, risk, allowed role and argument schema. Only the two read-only tools are allowed in AI proposals. There is no registration/update endpoint. Arguments are exact JSON objects: classify_issue requires nonblank subject (max 200) and body (max 4000), matching the submitted issue; search_documentation requires category ACCOUNT/BILLING/GENERAL matching the current classification. Additional fields, owner IDs, URLs, roles, approval flags and changed issue context are denied.

The planner returns this envelope; it never supplies permissions or execution settings:

```json
{"toolCalls":[{"tool":"classify_issue","arguments":{"subject":"Cannot log in","body":"Password reset failed"}}]}
```

Java validates the whole batch before execution and revalidates frozen arguments before dispatch. Strict parsing rejects unknown fields, duplicate JSON keys and trailing content. A successful agent step exposes plannerId, canonical proposal, policyStatus APPROVED, and decisionCode APPROVED. A denied step exposes policyStatus DENIED and a safe code, with null proposal; malformed/denied raw output is not stored. Java policy approval does not represent a human approval.

Limits are five planner calls and ten tool calls per run, one proposal per agent step, at most three tool calls per proposal (subject to the configured step budget), a 16 KiB UTF-8 proposal cap, and 120 active seconds from RUNNING for all runs, excluding persisted human approval waits. The worker checks elapsed budget before every step, including final actions. AI-proposable handlers remain bounded local functions; GitHub creation uses the separate approved external-action path below.

Safe failure codes include INVALID_PROPOSAL, PROPOSAL_TOO_LARGE, UNKNOWN_TOOL, TOOL_NOT_ALLOWED, INVALID_TOOL_ARGUMENTS, TOOL_CONTEXT_MISMATCH, TOOL_ROLE_DENIED, TOOL_BUDGET_EXHAUSTED, PLANNER_BUDGET_EXHAUSTED, RUN_DEADLINE_EXCEEDED and PLANNER_FAILURE. Failures terminate the run and cancel later steps. In deterministic mode, usage counts are committed local attempts and transaction rollback restores the previous usage/checkpoint. Live intent usage follows the lifecycle below. Planner failures expose no provider exception text.

## Live planner lifecycle

The server selects live mode for new agent runs when agentflow.planner.provider=openai or gemini; the client cannot supply a provider, principal or token limit. Provider mode and model identity are frozen with the run. A provider mismatch fails with PLANNER_UNAVAILABLE before dispatch; no automatic fallback occurs. Each call commits PLANNING_REQUESTED, one planner call and an 18,000-token reservation before network dispatch. The step remains PENDING with planningState REQUESTED and a 35-second planningDeadline. A matching result resolves the intent and passes Java policy before tool execution. Success stores reported plannerTokens and planningState RESOLVED.

Cancellation abandons the intent without waiting for the model and prevents late tools. Duplicate results do nothing. Unresolved intents are never resent after restart: they fail with PLANNING_OUTCOME_UNKNOWN at expiry. A result transaction rollback retains the earlier intent and reservation. Reservations cap application dispatch and remain consumed on uncertainty; reported tokens cover committed result transactions, not all potentially billed requests. Deterministic reservations/tokens remain zero.

Live calls cap input at 15,000 UTF-8 bytes, HTTP responses at 64 KiB, completions at 1,024 tokens and dispatch wait at 30 seconds. Total reserved tokens cannot exceed 90,000. Automatic retries and model/tool recursion are disabled. PLANNER_TIMEOUT, PLANNER_TOKEN_LIMIT, PLANNER_UNAVAILABLE and PLANNING_OUTCOME_UNKNOWN are terminal. HTTP 401/403 maps to PLANNER_AUTHENTICATION. Allowlisted 429 codes distinguish PLANNER_QUOTA and PLANNER_RATE_LIMIT; unknown 429 bodies use PLANNER_ACCESS_LIMIT. Provider body/exception details are discarded. Starting a new run is an explicit new request and may spend API tokens; request idempotency still reuses the original failed run.


## Operations monitoring

`GET /api/operations` requires a session and the current persisted USER role. Ownership comes from the principal; query parameters cannot select another owner. The read-only, `Cache-Control: no-store` response contains `capturedAt` (UTC), `totalRuns`, `pendingRuns`, `runningRuns`, `waitingRuns`, `succeededRuns`, `failedRuns`, `cancelledRuns`, `pendingApprovals`, `overdueApprovals`, `retryingSteps`, `unknownTickets` and `sendingTickets`.

Run counts include all retained runs, including archived workflow history. Pending approvals belong to owned waiting runs; overdue means the pending deadline is at or before `capturedAt`, awaiting worker expiry. Retry counts include PENDING steps with a persisted `nextAttemptAt` only on PENDING/RUNNING runs. UNKNOWN/SENDING intents count even on cancelled runs, because cancellation cannot undo an in-flight external action. One aggregate SQL statement reads a consistent database snapshot without returning inputs, payloads, IDs, tokens or error bodies. This endpoint neither checks provider availability nor changes execution state.

## Human approvals

A configured checkpoint omits `operation`/`agent`:

```json
{"id":"review","type":"APPROVAL","label":"Review result","approval":{"timeoutSeconds":3600}}
```

The timeout is 60–86,400 seconds. It must immediately precede an ACTION with `operation: RECORD_RESULT` or `CREATE_TICKET`. Ticket creation must be the final step. Java freezes `{targetNodeId, operation, input, context, ticket}` (ticket is null for local actions), its SHA-256 payloadDigest, run/step/target IDs, owner and fixed requiredRole USER. Both run and approval step wait. Approval time does not consume the 120-second active execution budget.

| Method | Route | Behavior |
| --- | --- | --- |
| GET | `/api/approvals?page=0&size=20` | Owner's PENDING inbox; same pagination bounds as workflows |
| GET | `/api/approvals/{id}` | Owner-only frozen payload, digest, deadline and decision metadata |
| POST | `/api/approvals/{id}/decision` | Owner with current USER role submits APPROVE or REJECT and exact payloadDigest |

Decision body:

```json
{"decision":"APPROVE","payloadDigest":"<64 lowercase hexadecimal characters from the reviewed approval>"}
```

Decision, pause and audit events are transactional under the same run lock as execution/cancellation. Same decision and digest are idempotent (200); a conflicting decision, digest mismatch, cancellation or expiry is 409. Missing/unowned requests return 404; revoked role is 403. Expiry at the exact deadline commits EXPIRED and FAILED before the decision endpoint returns 409 with the approval view. The worker also expires unanswered approvals. Rejection/expiry records APPROVAL_REJECTED/APPROVAL_EXPIRED and cancels later steps. Run cancellation resolves a pending approval as CANCELLED.

An approved action rechecks current role, payload integrity and the exact original issue, checkpoint, target and operation on every attempt. Changed inputs fail with APPROVAL_PAYLOAD_CHANGED; a revoked role fails with TOOL_ROLE_DENIED. No decision body can replace the payload or choose a role/owner. Approval views are also included in the owner-only run trace. Events include APPROVAL_REQUESTED, APPROVAL_APPROVED, APPROVAL_REJECTED, APPROVAL_EXPIRED and APPROVAL_CANCELLED.

## Local action retries

Only a Java-owned `LocalActions.TransientFailure` from the explicit pure ACTION allowlist (CLASSIFY_ISSUE, SEARCH_DOCUMENTATION, RECORD_RESULT) schedules a retry. There are at most three total attempts. After failed attempts one/two, the committed step stays PENDING with LOCAL_TRANSIENT_FAILURE and nextAttemptAt: 2/4 seconds plus 0–500 ms jitter. The run stays RUNNING. Early polls do nothing; due polls resume from the prior successful checkpoint. A successful retry clears the safe error and nextAttemptAt; exhausted retries fail the run and cancel later steps. Each schedule records STEP_RETRY_SCHEDULED without exception text.

Schedules, attempts and audits commit together; rollback preserves the prior state. Competing workers share the run lock. Cancellation clears the schedule and prevents future actions. The 120-second active deadline includes retry delays and is checked before dispatch. Approval checks are repeated before retrying its local action. Validation, role/approval denials, unclassified failures, agent batches, provider calls and uncertain remote outcomes are terminal. Database transaction failures retain the prior checkpoint for a later worker poll; they are not counted as committed handler attempts. Built-in handlers currently require no retry in normal operation. No external exactly-once guarantee is implied.


## Approved GitHub ticket action

Provider references: [create an issue](https://docs.github.com/en/rest/issues/issues#create-an-issue) and [get an issue](https://docs.github.com/en/rest/issues/issues#get-an-issue).

```json
{"id":"ticket","type":"ACTION","label":"Create GitHub issue","operation":"CREATE_TICKET","ticket":{"repository":"your-org/your-repo"}}
```

This action must be final and immediately after an approval checkpoint. Classification must already exist in the checkpoint. Java builds an exact title/body from the submitted issue and validated classification, adding an immutable node UUID correlation marker. The approval's `payload.ticket` contains `{repository,title,body}`. Models cannot propose this tool through AI steps or change its destination.

`GET /api/tools` includes the action's EXTERNAL_WRITE descriptor. `GET /api/tools/github` requires authentication and returns only `{repository,ready}`. Ready means opt-in configuration and a syntactically present token; it does not prove remote access. No token is returned.

An approved action commits a SENDING ticket intent, one attempt, one tool call and TICKET_DISPATCHED before HTTP. POST creation has a 10-second total wait and 64 KiB response cap, no redirects and no retries. The durable intent deadline is at most 20 seconds, capped by the active run deadline. `tickets` in the owner-only run trace records SENDING, SUCCEEDED, FAILED or UNKNOWN, safe errors, timestamps and confirmed issue number/URL. Run context also records a successful receipt. The receipt URL must be the fixed GitHub host, approved repository and returned issue number.

No fresh worker resends a persisted intent. Lost outcomes expire to TICKET_OUTCOME_UNKNOWN, including intents left in cancelled runs. Authentication errors are TICKET_AUTHENTICATION; definitive API rejection is TICKET_REJECTED. Invalid/oversized/malformed successes and uncertain transport/server errors are unknown. TICKET_CONNECTOR_DISABLED and TICKET_DESTINATION_DENIED prevent dispatch. A new run is a new action and can create another issue; never use it to retry an unknown result.

Cancellation before dispatch prevents creation; after dispatch it cannot retract HTTP. A late receipt remains in `tickets`, with TICKET_RESULT_AFTER_CANCELLATION, while the run remains CANCELLED.

`POST /api/runs/{id}/ticket/reconcile` accepts only `{"issueNumber":42}`. It requires session ownership, CSRF, current USER role and an UNKNOWN intent. Java revalidates the exact approved payload and current destination policy, then GETs that issue in the fixed repository and matches title, body, correlation marker and issue number (pull requests are rejected). It never POSTs another issue. Confirmed reconciliation records TICKET_RECONCILED and may move FAILED to SUCCEEDED; a cancelled run stays cancelled. Repeating an already confirmed issue number is idempotent. Missing/unowned intents are 404; policy or receipt mismatch is 409; denied role/CSRF is 403.
