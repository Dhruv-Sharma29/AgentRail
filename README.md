# AgentFlow

A workflow platform where AI proposes actions and a Java backend validates, authorizes, and controls execution.

AgentFlow implements authenticated workflow management, durable execution, and a Java-controlled planner/tool boundary: Java 21, Spring Boot 3.5.16, Spring AI 1.1.8, JPA, Flyway, session authentication with CSRF, owner-scoped APIs, immutable versions, run checkpoints, cancellation, tool validation, and execution audits. Live Gemini or OpenAI planning is opt-in. Human approvals, retries, and the React Flow frontend are subsequent increments.

## Run locally

Requires JDK 21 or newer. The Maven wrapper downloads the pinned Maven distribution and dependencies on first use.

```sh
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The dev profile binds to `127.0.0.1:8080`, uses file-backed H2 at `backend/data`, and seeds `demo` / `demo-local-only`. These public dev credentials apply only to the explicit dev profile. Stop with Ctrl-C.

With the server running, from the repository root:

```sh
python3 scripts/smoke-test.py
```

The smoke test creates and archives a sample workflow using real cookies and CSRF tokens. API details are in [docs/api-spec.md](docs/api-spec.md).

## Run the first workflow

Restart the backend after pulling/building changes. With it running, use a second terminal from the repository root:

```sh
python3 scripts/execution-demo.py
```

This saves [examples/support-intake.json](examples/support-intake.json), starts its immutable version, checks duplicate-start protection, and prints the completed trace and result. The four steps receive an issue, classify it with local keyword rules, look up a built-in documentation entry, and persist the result. No model is called and no external ticket is created. The saved workflow and run remain available through the authenticated API.

To try another issue:

```sh
python3 scripts/execution-demo.py --subject "Urgent billing problem" --body "My payment failed"
```

The worker advances one step per polling cycle, normally every second. Linear graphs can contain trigger, action, and configured agent steps. Approval, condition, branching, and unconfigured nodes remain editable but return 400 when started.

## Run the planner and tool demo

With the updated backend running, use a second terminal from the repository root:

```sh
python3 scripts/execution-demo.py --agent
```

This saves [examples/agent-support-intake.json](examples/agent-support-intake.json). Agent steps propose classification and documentation tools; Java validates the complete proposal, workflow allowlist, strict arguments, persisted user role, issue context and budgets before running a local handler. The trace prints planner identity, proposed tools, Java policy decisions and usage. A successful run records two planner calls, two tool calls and thirteen audit events.

The default mode is `DETERMINISTIC_PLANNER`: no live model is called. The registered tools are read-only local capabilities. `GET /api/tools` exposes their metadata to authenticated users; there is no tool-registration API.

## Opt in to live planning

### Gemini

Configure `GEMINI_API_KEY` in the ignored repository `.env.local` file. Obtain the key from [Google AI Studio](https://aistudio.google.com/apikey); keep it out of source control and chat. Stop any existing backend, then run:

```sh
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,gemini
```

In IntelliJ, select the shared **AgentFlow Gemini** run configuration instead of **AgentFlow Dev**. Run only one backend against the local H2 database; a second instance fails with a database-in-use error. The working directory must be `backend` so the profile finds `../.env.local`.

In a second terminal from the repository root:

```sh
python3 scripts/execution-demo.py --agent
```

New runs report `GEMINI_PLANNER`. The default model is `gemini-3.5-flash-lite`; `AGENTFLOW_PLANNER_MODEL=gemini-3.8-flash` is also supported. Google documents [function calling through its OpenAI-compatible endpoint](https://ai.google.dev/gemini-api/docs/openai), so the existing Spring AI client sends requests to the fixed Google host without another SDK. No OpenAI request or OpenAI credential is needed for Gemini planning. Each run freezes its provider and model; switching server configuration cannot redirect an existing run to another provider.

The live Gemini support demo passed with two planner calls, two Java-approved tool calls, 450 reported tokens and fifteen audit events. Classification remains the local keyword tool; no external ticket is created. Deadlines, input/response limits, token reservations, manual execution and disabled provider retries apply to both live providers. An ambiguous Google 429 is recorded as `PLANNER_ACCESS_LIMIT`; authentication errors are redacted. Gemini access and quotas depend on the key's Google project.

### OpenAI

After secure credential setup, the ignored repository `.env.local` contains OPENAI_API_KEY. Stop the existing server, then run from `backend`:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,openai
```

Use `python3 scripts/execution-demo.py --agent` in another terminal from the repository root. New agent runs report OPENAI_PLANNER. The model proposes existing tools; classification itself remains a local rule-based tool. A successful four-step live run has two planner calls, two tool calls and fifteen audit events, plus reported and reserved token counts.

The OpenAI profile loads `../.env.local` as properties; production may inject OPENAI_API_KEY and AGENTFLOW_PLANNER_PROVIDER=openai through its secret/environment system. Do not commit credentials. The default model is gpt-4.1-mini-2025-04-14; the adapter also accepts gpt-4.1-mini. The current provider endpoint is fixed to api.openai.com.

Each call persists a planning intent before dispatch outside database transactions. Java rejects cancelled, expired, malformed or unauthorized results before tools execute. Input, responses, output tokens and elapsed time are bounded. Automatic retries are disabled; unknown outcomes fail after the intent deadline instead of resending. Reservations remain consumed on failure/cancellation, and are conservative limits rather than billed-token measurements.

The local live smoke request returned HTTP 429 and safely executed zero tools. Its precise rate-limit/quota cause was not captured by the original handler; successful live completion remains unverified. Current code distinguishes PLANNER_RATE_LIMIT, PLANNER_QUOTA and ambiguous PLANNER_ACCESS_LIMIT using allowlisted provider codes. Resolve the reported limit before starting another live run; offline deterministic mode remains available.

## Test and build

```sh
cd backend
./mvnw test      # Graph, API, authentication, ownership, and concurrency tests with H2
./mvnw package   # Tests and executable jar
./mvnw verify    # Also runs PostgreSQL Testcontainers tests; requires a running Docker daemon
```

From the repository root, after building the jar and with Docker running:

```sh
python3 scripts/restart-test.py
python3 scripts/restart-test.py --agent
```

Each restart check uses its own PostgreSQL container and API port. It kills its own Java process after a committed checkpoint, starts a fresh process, and verifies the remaining steps execute once. Agent mode also checks the persisted proposal and usage counters. It cleans up its processes and container. CI runs verification and both restart modes.

Tests use an explicitly configured Mockito Java agent, avoiding dynamic self-attachment on newer JDKs. CI runs verify on Java 21 with Docker. H2 tests do not establish PostgreSQL compatibility.

## Local PostgreSQL and containers

Copy `.env.example` to `.env` and set unique DATABASE_PASSWORD and BOOTSTRAP_PASSWORD values. Bootstrap passwords must be 12–72 characters. Then run:

```sh
docker compose up --build
```

Compose binds API and database ports to loopback and keeps PostgreSQL data in a named volume. Its HTTP session setting is for local use. The default application configuration uses environment-supplied database and bootstrap credentials and Secure cookies; use HTTPS before exposing it remotely. The bootstrap user is created only when the username does not already exist; changing the environment password does not rotate an existing user's password.

Registration, password rotation, login throttling, production ingress, remote-tool reliability, and approvals remain on the implementation plan. The API currently has a single USER role; approver and tool administration roles arrive with those modules. Execution audits cover lifecycle and step outcomes; authentication and administrative audits are future work.

## Project documents

- [MVP specification](docs/specification.md)
- [Current architecture](docs/architecture.md)
- [Phases and acceptance criteria](docs/implementation-plan.md)
- [API contract](docs/api-spec.md)
- [Skill installation and provenance](docs/skills.md)
- [Verification and review](docs/verification.md)

Project-specific skills are under `.agents/skills`. Newly installed global and project skills become discoverable on the next Codex turn.
