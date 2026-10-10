# AgentFlow MVP

AgentFlow is a workflow platform: users design graphs, a planner proposes approved tool calls, and a Java backend validates and controls execution. The AI is never an authorization or execution boundary.

## Architecture

Use Java 21, Spring Boot 3.5, Spring Security, JPA, Bean Validation, PostgreSQL, and Flyway in a modular monolith. A later React/TypeScript client uses React Flow. Deploy one API and one database initially. Package boundaries: auth, workflow, execution, agent, tools, approvals, audit.

The API owns workflow authorization, graph validation, immutable versions, run transitions, tool policies, and approval decisions. Planner/provider adapters return proposals only. Tool implementations perform effects only after the executor has validated the proposal and its authorization.

## Product contract

- Users own workflows and can create, list, inspect, update, and archive them.
- Updating a workflow creates an immutable version. A run binds to one version permanently.
- Node types are trigger, AI step, condition, approval, and action. The MVP accepts directed acyclic graphs with one trigger and no disconnected nodes. Execution semantics for branches are added in the engine phase.
- The initial workflow is support intake → classification → documentation search → human approval → ticket creation.
- A deterministic runtime proves engine behavior first. A proposal planner follows in Phase 3. Live model integration is a separate phase and must be visibly distinguished from deterministic mode.
- Backend-registered tools carry strict argument schemas, allowed roles, risk policy, limits, and idempotency behavior. Models cannot register tools or override those policies.
- Approvals persist the exact proposal. Approved arguments cannot change without another approval. Rejected or cancelled runs cannot execute subsequent effects.
- Every run, node attempt, validation decision, approval, and outcome receives persisted status and audit records. Logs must exclude credentials and unnecessary user content.

## Data contract

Use UUID identifiers and UTC timestamps. Foundation tables: users, workflows, workflow_versions. Execution adds workflow_runs, node_executions, tool_definitions, approval_requests, audit_events, and a local demo ticket table.

Runs use PENDING, RUNNING, WAITING_FOR_APPROVAL, SUCCEEDED, FAILED, CANCELLED. Node attempts have separate status, inputs, outputs, and safe error metadata. Run idempotency keys bind owner, workflow, and input; reuse with different input conflicts. Side-effect tools require their own deduplication/reconciliation guarantees.

## Security boundaries

Session authentication with BCrypt password hashes and CSRF protection. The principal supplies ownership; request bodies cannot choose an owner. Object-level authorization covers all reads, updates, archives, versions, runs, approvals, and audits. Unknown request fields fail validation. Local development credentials are scoped to an explicit dev profile; production configuration requires environment-supplied values.

Tool proposals are untrusted, including anything derived from retrieved documents. Enforce tool allowlists and schemas in Java. No arbitrary shell, SQL, filesystem, or user-selected outbound URL tools. Later remote tools need SSRF controls, deadlines, response-size limits, and failure classification.

## MVP exclusions

Microservices, marketplace tools, arbitrary code nodes, unrestricted browser agents, automatic side effects, multi-organization sharing, billing, and local model support are deferred. Redis and a durable workflow product require a demonstrated execution need.


## Implemented Phase 4 contract

The support demo now executes receive → classify → documentation → owner approval → local result. Configured approvals freeze the exact original issue, checkpoint, target operation, owner and required USER role; rejected, expired and cancelled requests cannot execute the result. Independent approvers and remote ticket creation are future increments. Pure ACTION retries are Java-classified, limited to three attempts with persisted backoff/jitter, and subject to current approval/role checks and a 120-second active run deadline. Human waiting is excluded. Provider/agent requests with uncertain outcomes are never automatically replayed.
