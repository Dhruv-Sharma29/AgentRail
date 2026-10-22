# 05 — Configuration

Configuration belongs to the Java server or deployment environment. The browser cannot select a provider, supply an API key, grant tool permissions or change the approved GitHub destination.

AgentRail was previously named AgentFlow. Configuration keys (`AGENTFLOW_*`, `agentflow.*`), database names, session cookie names and Java package/artifact identifiers keep their original spelling. Use the names shown here; renaming these values is not required for the new product name. Existing ticket payload text and correlation markers also remain unchanged so saved approvals and reconciliation stay valid.

## Profiles and environment files

| Launch mode | Data store | Private configuration | Planner |
| --- | --- | --- | --- |
| `dev` | File-backed H2 | Repository `.env.local`, optionally imported | Deterministic by default |
| `dev,gemini` | File-backed H2 | Repository `.env.local` | Gemini |
| `dev,openai` | File-backed H2 | Repository `.env.local` | OpenAI |
| Docker Compose | PostgreSQL named volume | Repository `.env` | Explicitly deterministic in Compose |
| Default application profile | PostgreSQL via required environment variables | Deployment environment | Deterministic unless configured |

Run Maven from `backend/`: the development/provider profiles resolve `../.env.local` relative to that working directory. The file uses properties syntax, one `NAME=value` per line, without `export`. It is optional and ignored by Git. Create or edit it locally; preserve any existing values.

Compose reads `.env`, not `.env.local`. Use `python3 scripts/init-local.py` to initialize Compose credentials, or fill the [template](../.env.example) yourself. The script preserves existing files. Keys must never be placed in `VITE_*` variables or frontend code.

Restart the backend after configuration changes. Environment password changes do not reset an existing bootstrap account or PostgreSQL role; initialization only seeds new accounts/databases.

## Database, account and web settings

| Variable | Default / requirement | Scope |
| --- | --- | --- |
| `DATABASE_URL` | Required outside `dev` | JDBC URL; Compose supplies its internal PostgreSQL URL |
| `DATABASE_USERNAME` | Required outside `dev` | Database login; Compose supplies `agentflow` |
| `DATABASE_PASSWORD` | Required for Compose/default configuration | Database password |
| `DATABASE_NAME` | `agentflow` in Compose | Database selected by the stack |
| `BOOTSTRAP_USERNAME` | Required by default; Compose defaults to `admin` | Initial USER account |
| `BOOTSTRAP_PASSWORD` | Required outside `dev`; 12–72 characters | Initial account password |
| `PORT` | `8080` | Java HTTP port; changing it also requires matching proxy configuration |
| `WEB_PORT` | `8090` | Compose loopback web port |
| `SESSION_COOKIE_SECURE` | `true` by default | `dev` and local Compose explicitly disable it for HTTP |

Sessions expire after 30 minutes and are held in the Java process. After a backend restart, sign in again. Workflows, runs and approvals remain in the database.

## Optional live planning

Agent nodes use the configured planner. Pure ACTION nodes continue to run local Java handlers. The local classifier remains rule-based even when the model proposes calling it.

| Variable | Purpose |
| --- | --- |
| `AGENTFLOW_PLANNER_PROVIDER` | `deterministic`, `gemini` or `openai`; provider profiles select the latter two |
| `AGENTFLOW_PLANNER_MODEL` | Optional supported model override, frozen when a run is created |
| `GEMINI_API_KEY` | Server-side Gemini credential |
| `OPENAI_API_KEY` | Server-side OpenAI credential |

### Gemini

Add `GEMINI_API_KEY` to your private `.env.local`. Stop the existing backend, then start from `backend/`:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,gemini
```

The repository's configured default model is `gemini-3.5-flash-lite`. The adapter uses Google's fixed OpenAI-compatible endpoint through Spring AI. Availability and quota depend on the provider account; the default is a project setting, not a guarantee of provider access.

### OpenAI

Add `OPENAI_API_KEY` to your private `.env.local`. Stop the existing backend, then start from `backend/`:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,openai
```

The repository's configured default is `gpt-4.1-mini-2025-04-14`, using the fixed OpenAI API host. Provider configuration does not bypass Java policy. The OpenAI adapter is tested offline with mocked responses; a successful live OpenAI run has not been recorded.

### Verify planner selection

From the repository root, with the chosen backend running:

```sh
python3 scripts/execution-demo.py --agent
```

New runs report `DETERMINISTIC_PLANNER`, `GEMINI_PLANNER` or `OPENAI_PLANNER`. This command makes live model requests when a live provider is enabled and may incur provider charges. It executes local tools and records a local result.

Existing runs retain their provider/model identity. Changing configuration affects new runs; pending live runs fail closed if their original provider is unavailable. There is no automatic fallback or provider retry. Supported model identifiers are validated by the adapters; arbitrary model names are not assumed to work.

Compose hardcodes deterministic planning. Adding a model key to `.env` alone does not enable live planning in the container stack; a deliberate backend environment override is required.

## Optional GitHub issue creation

The connector is disabled by default. Configure the following server-side values in `.env.local` for development, or `.env` for Compose:

```properties
AGENTFLOW_GITHUB_ENABLED=true
AGENTFLOW_GITHUB_REPOSITORY=your-org/your-repo
GITHUB_TOKEN=<repository-scoped token>
```

Replace the placeholder locally. Use a token limited to the intended repository and issue-writing capability. Keep it out of screenshots, source control, logs and chat. The workflow's repository must match the server setting exactly. Restart the development backend, or recreate the Compose backend using the [operations procedure](07-operations.md#stop-restart-and-preserve-history).

The Tool registry readiness indicator checks configuration only. Remote permissions are evaluated when a request is made. A configured connector does not start any workflow or authorize any issue by itself. The final ticket action requires a human decision over the exact frozen repository, title and body. [User workflow](02-user-guide.md#create-an-approved-github-issue) · [Execution contract](04-api-reference.md#approved-github-ticket-action).

## Execution settings and limits

The worker properties are `agentflow.execution.worker-enabled` (default `true`) and `agentflow.execution.poll-delay-ms` (default `1000`). Disabling the worker stops automatic advancement while retaining state. Tests control these explicitly.

Tool allowances, retry eligibility, proposal limits, call budgets and approval checks are server policy. They are not workflow-supplied permissions. See [the API reference](04-api-reference.md) for exact limits and [architecture](03-architecture.md) for recovery behavior.
