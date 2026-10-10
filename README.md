# AgentFlow

A workflow platform where AI proposes actions and a Java backend validates, authorizes, and controls execution.

AgentFlow implements authenticated workflow management, durable execution, and a Java-controlled planner/tool boundary: Java 21, Spring Boot 3.5.16, Spring AI 1.1.8, JPA, Flyway, session authentication with CSRF, owner-scoped APIs, immutable versions, run checkpoints, cancellation, tool validation, and execution audits. Live Gemini or OpenAI planning is opt-in. Durable human approvals and bounded pure local action retries are implemented. The React Flow workspace includes visual editing, immutable version history, live execution traces and an approval inbox.

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

## Open the visual workspace

Keep the backend running. In a second terminal from the repository root:

```sh
cd frontend
npm ci
npm run dev
```

Requires Node 22.12+ (Node 24 is used in CI). Open [AgentFlow](http://127.0.0.1:5173) and sign in with `demo` / `demo-local-only` on the dev backend. The frontend entry point is `frontend/src/main.tsx`; launch it through Vite, not an individual source file. The Java entry point remains `AgentFlowApplication`.

Click **New workflow**, edit the support template, and **Save workflow**. Drag nodes to arrange them; connect handles or use the keyboard connection controls. Select an edge and press Delete to disconnect it. Each save creates a version; stale edits retain your draft. The current engine supports one linear chain of triggers, local actions, configured agents and approval checkpoints.

Click **Run workflow**, enter an issue and follow its execution trace. At the human checkpoint, open **Review approval**, inspect the frozen action, check the review box and explicitly approve or reject. You can also review your existing terminal-created request in **Approvals**; leave its decision to the person reviewing it. A saved workflow never runs automatically. The default new template uses local actions; the Agent template uses the backend's configured planner and may make paid calls when live planning is enabled.

Vite proxies `/api` and `/actuator` to the loopback backend on 8080. Session cookies and CSRF remain enforced by Java. Credentials and provider keys stay out of the bundle. Stop Vite with Ctrl-C. The container setup below serves the compiled frontend and API through one origin.

The sign-in page includes an animated support-workflow preview with pause/play controls and a static reduced-motion mode. It is an illustration; it does not start workflows, decide approvals, or display live usage. The reusable component is `frontend/src/components/ui/ai-agent-pipeline.tsx`, imported with `@/components/ui/ai-agent-pipeline`. The `@` alias maps to `frontend/src` in both Vite and TypeScript. Shared styles are in `frontend/src/styles.css`; Tailwind 4 and TypeScript are already configured.

Reusable UI components belong in `frontend/src/components/ui`. The real workflow editor uses `n8n-workflow-block-shadcnui.tsx`: typed node cards, curved connections, a keyboard-accessible Add node menu and counts from the saved draft. React Flow owns the drag/connection behavior; the parent editor persists positions and validates the graph. Only supported AgentFlow steps appear in the menu. Execution and approval decisions remain controlled by Java.

The selected **Console** theme uses charcoal surfaces, mint actions, amber approval checkpoints, compact navigation and monospace execution details. It applies to sign-in, workflows, traces, approvals, tools and dialogs, including when the operating system uses light mode. Shared semantic colors and keyboard focus styles live in `frontend/src/styles.css`.

`frontend/components.json` configures shadcn for Tailwind 4, the existing stylesheet and `@` aliases. Badge, Button, Card and Dropdown Menu were installed with the official shadcn 4.21.4 CLI and use the Console theme tokens. To preview another primitive, run `npx shadcn@latest add <component> --dry-run` from `frontend`, then review its files, dependencies and theme changes before installing it. The builder is loaded separately from the initial page bundle. See the [shadcn Vite setup](https://ui.shadcn.com/docs/installation/vite).

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

The worker advances one step per polling cycle, normally every second. Linear graphs can contain trigger, action, configured agent steps and configured approval checkpoints. Condition, branching and unconfigured nodes return 400 when started.

## Run the planner and tool demo

With the updated backend running, use a second terminal from the repository root:

```sh
python3 scripts/execution-demo.py --agent
```

This saves [examples/agent-support-intake.json](examples/agent-support-intake.json). Agent steps propose classification and documentation tools; Java validates the complete proposal, workflow allowlist, strict arguments, persisted user role, issue context and budgets before running a local handler. The trace prints planner identity, proposed tools, Java policy decisions and usage. A successful run records two planner calls, two tool calls and thirteen audit events.

The default mode is `DETERMINISTIC_PLANNER`: no live model is called. The registered tools are read-only local capabilities. `GET /api/tools` exposes their metadata to authenticated users; there is no tool-registration API.

## Review a human approval

Restart the updated backend, then run from the repository root:

```sh
python3 scripts/approval-demo.py
```

The run pauses at `WAITING_FOR_APPROVAL` and prints the exact proposed local result action. Read it and enter `approve` or `reject`. Approval resumes execution; rejection prevents the result. The script verifies a repeated identical decision is idempotent. The saved payload, digest, decision and audit trail survive process restart.

Use `python3 scripts/approval-demo.py --agent` to include agent planning with the server's configured provider. A Gemini/OpenAI server makes live model requests for those steps. Approval requires the authenticated workflow owner with the current USER role; independent reviewers and external ticket creation are future work. The demo freezes the issue, context and target operation before approval, then Java checks them again before execution.

Pure ACTION handlers can retry only a Java-classified transient failure: three total attempts, 2/4-second backoff plus up to 500 ms jitter, persisted `nextAttemptAt`, and a 120-second active run budget. Approval wait time is excluded. Validation denials, unclassified errors, agent batches and provider requests are never automatically retried. Built-in handlers normally succeed immediately; retry tests use controlled faults. External tools will need separate idempotency and reconciliation.

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

Stop any dev process running `target/agentflow-0.1.0-SNAPSHOT.jar` before rebuilding that same jar, then restart it afterward. Replacing a running Spring Boot archive can break later class loading. `spring-boot:run` and the container runtime have separate launch/build paths.

```sh
cd backend
./mvnw test      # Graph, API, authentication, ownership, and concurrency tests with H2
./mvnw package   # Tests and executable jar
./mvnw verify    # Also runs PostgreSQL Testcontainers tests; requires a running Docker daemon
```

Frontend checks (from `frontend`, after `npm ci`):

```sh
npm run format:check
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

Browser tests require the built backend jar. They launch their own deterministic in-memory H2 backend on 18085 and Vite on 5174, then clean up both; they do not use the development database or a live provider. CI checks both Java and the frontend and retains failed browser traces/screenshots.

From the repository root, after building the jar and with Docker running:

```sh
python3 scripts/restart-test.py
python3 scripts/restart-test.py --agent
python3 scripts/restart-test.py --agent --approval
```

Each restart check uses its own PostgreSQL container and API port. It kills its own Java process after a committed checkpoint, starts a fresh process, and verifies the remaining steps execute once. Agent mode also checks the persisted proposal and usage counters. It cleans up its processes and container. Approval mode kills the process while waiting for review, checks that the same frozen payload remains paused, and resumes only after an explicit test decision. CI runs verification and all three restart checks.

Tests use an explicitly configured Mockito Java agent, avoiding dynamic self-attachment on newer JDKs. CI runs verify on Java 21 with Docker. H2 tests do not establish PostgreSQL compatibility.

## Local PostgreSQL and containers

Requires Docker Desktop with Compose, Python 3, and a running Docker daemon. From the repository root:

```sh
python3 scripts/init-local.py
docker compose up -d --build --wait --wait-timeout 180
```

Open [the container workspace](http://127.0.0.1:8090). Read `BOOTSTRAP_USERNAME` and `BOOTSTRAP_PASSWORD` in your private, ignored `.env` to sign in. The initializer generates independent random passwords with file mode 0600 and preserves an existing file. For manual setup use `.env.example`; bootstrap passwords must be 12–72 characters.

Compose serves the compiled React app through Nginx. Only the web port is published, on loopback; the Java API and PostgreSQL are internal. Database history survives `docker compose down` and container recreation in the named volume. This database is separate from development H2; existing H2 history is not imported. Docker defaults to deterministic planning and does not load `.env.local` or make model calls. Your Vite/dev servers can run alongside it.

The HTTP cookie setting is for local use. Public deployment requires a TLS ingress and Secure cookies; it is not configured here. The bootstrap user is created only when the username does not already exist; changing the environment password does not rotate an existing user's password. See [operations and backup/restore](docs/operations.md) before changing an existing database.

To verify the actual deployment in a disposable project:

```sh
cd frontend
npm ci
npx playwright install chromium
cd ..
python3 scripts/test_local_setup.py
python3 scripts/compose-test.py --build --browser
```

The check uses random project/port names, temporary credentials and its own database volume. It verifies security headers, Java authentication/CSRF, exact approvals, backend replacement, PostgreSQL backup restore, complete stack recreation and the browser suite. It removes only its own containers/volume. A successful run writes `artifacts/agentflow-demo.webm` and `artifacts/agentflow-execution.png`; those generated artifacts are ignored by Git. [Demo guide](docs/demo-script.md).

Registration, password rotation, login throttling, production ingress, additional remote tools and independent approver roles remain on the implementation plan. The API currently has a single USER role; approver and tool administration roles arrive with those modules. Execution audits cover lifecycle and step outcomes; authentication and administrative audits are future work.

## Approved GitHub issues

The Console editor supports a final **Create GitHub issue** action immediately after human approval. Its configured destination is `Dhruv-Sharma29/test`. The inbox shows the exact repository, title and body; a confirmed run shows a GitHub issue link. Java dispatches one durable intent and never automatically retries an uncertain creation. A run with UNKNOWN outcome offers **Verify existing issue**, which only reads and matches an existing issue.

The connector is disabled by default. For development, add these values to the existing ignored `.env.local` file without sharing the token in chat:

```properties
AGENTFLOW_GITHUB_ENABLED=true
AGENTFLOW_GITHUB_REPOSITORY=Dhruv-Sharma29/test
GITHUB_TOKEN=<fine-grained token restricted to this repository with Issues write permission>
```

Restart the backend with your usual `dev,gemini` profile. For Compose, use the private `.env` instead and recreate the backend. Tool registry shows readiness without returning the token. Readiness checks configuration; the first approved request still validates actual GitHub access. Keep the connector disabled until ready to authorize a real issue.

Open a support workflow, select its last action and choose **Create GitHub issue**. Save the version, start the run, then inspect and explicitly approve its ticket in the inbox. Cancellation after dispatch cannot remove an external issue; the trace retains any late receipt. On an unknown result, check the repository and reconcile its issue number rather than starting another run. [Detailed behavior and limitations](docs/github-ticket-tool.md).

## Project documents

- [MVP specification](docs/specification.md)
- [Current architecture](docs/architecture.md)
- [Phases and acceptance criteria](docs/implementation-plan.md)
- [Visual workspace acceptance criteria](docs/phase-5-frontend.md)
- [Container delivery acceptance criteria](docs/phase-6-delivery.md)
- [Operations and backup/restore](docs/operations.md)
- [Portfolio demo guide](docs/demo-script.md)
- [API contract](docs/api-spec.md)
- [Skill installation and provenance](docs/skills.md)
- [Verification and review](docs/verification.md)
- [Delivery security review and remaining dependency findings](docs/security-review.md)

Project-specific skills are under `.agents/skills`. Newly installed global and project skills become discoverable on the next Codex turn.
