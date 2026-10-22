# 00 — Product specification

## Purpose

AgentRail lets users design workflows, run registered tools under Java policy, review sensitive actions and inspect the resulting execution history. It demonstrates an explicit separation between model proposals and application authority.

The primary scenario is support intake: receive an issue, classify it, retrieve a built-in documentation entry, review the proposed action and record a result or create an approved GitHub issue.

## Current product contract

| Area | Implemented behavior |
| --- | --- |
| Accounts | Bootstrap USER account, BCrypt password hashing, session login/logout and CSRF |
| Workflows | Owner-scoped creation, listing, editing, archive and immutable version history |
| Editor | React Flow nodes/edges, persisted layout, strict DTO conversion and stale-edit handling |
| Execution | Single linear plan bound to a saved version, durable checkpoints, run idempotency and cancellation |
| Planning | Local deterministic planner; optional Gemini/OpenAI proposals with strict Java validation |
| Tools | Code-controlled local classification/search and a separately approved GitHub ticket action |
| Approvals | Frozen payload/digest, owner decision, expiry, durable pause/resume and execution revalidation |
| Reliability | Bounded pure-action retries, persisted planning/ticket intents and explicit uncertainty handling |
| Visibility | Step traces, usage, ordered audit events and owner-scoped operational counts |
| Delivery | Standalone H2 development; compiled frontend, Java and PostgreSQL through local Compose |

## Workflow semantics

Saving validates a directed acyclic graph with exactly one trigger, unique node IDs, valid edges and no disconnected nodes. This is separate from run validation. The persisted schema recognizes TRIGGER, ACTION, AI_STEP, APPROVAL and CONDITION; the current engine executes only a configured **single chain** without conditions or branching.

A run binds permanently to a workflow version. Editing or archiving the workflow cannot alter that plan. Saving does not execute anything. A new run of an archived workflow is denied; a replay of an existing idempotent request returns its original run.

Local operations receive input, classify with keyword rules, search a built-in catalog and record a result. An AI step proposes the classification/search tools; Java checks the entire batch before execution. Live planning does not make the classification handler generative.

An approval checkpoint immediately precedes `RECORD_RESULT` or a final `CREATE_TICKET` action. The ticket action uses one server-owned repository and requires classification context. A model cannot propose or directly dispatch `create_ticket`.

## Identity and data

Identifiers use UUIDs and execution timestamps use UTC. The eight persisted tables are:

| Table | Responsibility |
| --- | --- |
| `users` | Accounts, password hashes and roles |
| `workflows` | Ownership, current version and archive state |
| `workflow_versions` | Immutable definition and metadata snapshots |
| `workflow_runs` | Version binding, lifecycle, input, checkpoint and usage |
| `node_executions` | Ordered steps, outcomes, planning intents and retry schedules |
| `approval_requests` | Frozen actions, digests, deadlines and decisions |
| `ticket_executions` | Durable GitHub intents, uncertainty and confirmed receipts |
| `audit_events` | Ordered lifecycle, policy, decision and outcome events |

Tool definitions are code-controlled; there is no `tool_definitions` table or dynamic tool-registration API. Flyway owns schema changes and Hibernate validates the result.

## Lifecycle and recovery

Run states are `PENDING`, `RUNNING`, `WAITING_FOR_APPROVAL`, `SUCCEEDED`, `FAILED` and `CANCELLED`. Rejection and expiry fail a waiting run; explicit cancellation preserves completed work and prevents later steps.

Run idempotency binds owner, workflow, key, version and exact input. It does not deduplicate all future runs or guarantee exactly-once external effects. Pure local checkpoints and audits commit atomically. Provider and GitHub requests persist an intent before network dispatch and resolve it afterward.

Only Java-classified transient failures of explicitly allowed pure ACTION handlers receive retries: at most three attempts with persisted backoff/jitter. All runs have a 120-second active execution budget, excluding human approval wait time. Provider requests and uncertain external effects are never automatically replayed. Exact request limits are in the [API reference](04-api-reference.md).

## Acceptance scenarios

| Scenario | Required outcome |
| --- | --- |
| Two edits use the same expected version | One new version and one conflict; no lost update |
| Another user requests an owned resource | No disclosure; same 404 behavior as a missing resource |
| A start request is repeated unchanged | Original run returned without another execution |
| A planner proposes an unauthorized tool or changed issue | Java denies the proposal before handler execution |
| A run reaches human review | Exact action persists while later work remains paused |
| Approval is rejected, expired or cancelled | Later action cannot execute |
| Local work crashes around a transaction boundary | Committed checkpoint retained; uncommitted work rolled back |
| A GitHub request loses its response | Outcome retained as uncertain; no blind resend |
| A known issue is reconciled | Read-only exact-content verification records the receipt |
| Operational counts cannot refresh | Prior snapshot marked stale; no false healthy-zero display |

These are product contracts. See [testing](08-testing.md) for how they are checked.

## Boundaries and future work

Branching execution, parallel execution, arbitrary code/URL tools, tool marketplaces, recurring triggers, multi-organization sharing, billing and local-model support are outside the current implementation. Independent approvers, account registration/rotation, complete security auditing and automated data retention are also future work.

The scheduled worker is serial; a bounded provider request can delay other runs. Horizontal throughput and large-history load behavior have not been established. Redis, microservices or another workflow engine should follow a demonstrated requirement rather than be prerequisites for this implementation.

The included deployment is local. Public release depends on the [security follow-ups](06-security.md#before-public-deployment) and [operational requirements](07-operations.md#deployment-boundary).
