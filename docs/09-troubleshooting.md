# 09 — Troubleshooting

Start with the run's safe error code, backend health and the launch mode. Development uses ports 5173/8080 and H2; Compose uses port 8090 and PostgreSQL. These environments have separate accounts and data.

## Startup and sign-in

| Symptom | Check and next action |
| --- | --- |
| `cmd` is not found on macOS | Use `./mvnw` from `backend/`, not the Windows `mvnw.cmd` wrapper. |
| Java cannot start or compile | Check `java -version` and `JAVA_HOME`; use JDK 21 or newer. |
| npm reports an unsupported engine | Check `node --version`; the frontend requires Node 22.12+, and CI uses 24. |
| Port 8080 is already in use | Stop the earlier AgentRail terminal/IDE run before starting a replacement. |
| H2 database is already in use | Run one backend against `backend/data/`. Stop the old process; do not delete the database to clear the symptom. |
| Browser cannot reach the app | Keep both development servers running and open `http://127.0.0.1:5173`; for Compose, use its configured web port. |
| Development credentials fail in Compose | Use bootstrap credentials from private `.env`; `demo` belongs to the development database. |
| Login expired after restart | Sessions are process-local. Sign in again; durable workflow/run history is retained. |
| New bootstrap password is ignored | Bootstrap creates missing accounts only. Changing an environment value does not reset an existing password. |
| Mutation returns 403 | Preserve the session cookie and fetch a fresh CSRF token after login; execution-sensitive operations also require the current USER role. |

Use one host spelling consistently. Browser cookies for `localhost` and `127.0.0.1` are not interchangeable.

## Workflow and planner errors

| Symptom / code | Meaning and next action |
| --- | --- |
| Save succeeds but start returns 400 | Structural validity is not executable validity. Use one configured linear chain; branches and conditions cannot execute. |
| Update returns 409 | The saved version changed. Review the latest version and reconcile your draft. |
| Repeated start returns the same failed run | The idempotency key binds the original request. A new key is new work, not a repair of that run. |
| Run waits for approval | Open the frozen request and explicitly decide. The engine does not auto-approve it. |
| Approval returns 409 | Refresh the request; its digest, decision, expiry or run state may have changed. Review again before deciding. |
| `PLANNER_QUOTA`, `PLANNER_RATE_LIMIT`, `PLANNER_ACCESS_LIMIT` | Inspect the provider account's access/quota. No provider request is automatically retried. Use the deterministic profile for offline development. |
| `PLANNER_AUTHENTICATION` | Check the server-side key and active profile locally, then restart. Do not paste the key into logs or chat. |
| `PLANNER_UNAVAILABLE` | The frozen run provider/model is unavailable under current configuration. New configuration affects new runs. |
| `PLANNING_OUTCOME_UNKNOWN` | A request had no committed result before its intent expired. The engine does not resend a potentially billed request. |
| `TOOL_NOT_ALLOWED` / `TOOL_CONTEXT_MISMATCH` | Java rejected the proposal. Inspect the saved allowlist and original context; do not relax policy to make an unsafe proposal pass. |
| `RUN_DEADLINE_EXCEEDED` | The active execution budget elapsed. Human waiting is excluded; retry delays are included. |

For live planning, launch from `backend/` with `dev,gemini` or `dev,openai`. Check the mode on a newly created agent run. Running an ACTION-only workflow does not invoke a planner.

## GitHub outcomes

`TICKET_CONNECTOR_DISABLED` means the opt-in connector is unavailable. `TICKET_DESTINATION_DENIED` means the workflow destination does not match server policy. Readiness indicates configuration, not verified GitHub permissions. `TICKET_AUTHENTICATION` requires checking the server token and access to the configured repository.

For `TICKET_OUTCOME_UNKNOWN`, inspect the target repository and use **Verify existing issue** with a matching issue number. Do not start another run as a blind retry: the original request may have created the issue. Reconciliation only reads and validates; it cannot create a replacement. If there is no matching issue, the outcome remains unresolved until investigated.

## Docker and tests

Run from the repository root:

```sh
docker compose ps
docker compose logs --tail=100 backend frontend postgres
curl --fail http://127.0.0.1:8090/actuator/health
```

Keep diagnostic output bounded and redact private input before sharing. Do not dump container environments. If Docker commands cannot connect, start the daemon before rerunning Compose or Testcontainers checks. Cold dependency downloads may take longer than a warm build; inspect the failing stage before retrying.

If PostgreSQL credentials changed while reusing a volume, initialization variables do not change the existing database role password. Follow an explicit credential-rotation procedure; do not delete the volume to resolve login failures.

Browser tests need the backend jar and Playwright Chromium. Follow [testing](08-testing.md) in order. They use separate test ports and in-memory data. Avoid rebuilding an executable jar while a process is running directly from that same jar; stop and restart that process around packaging.

An Operations refresh error retains stale counts. A healthy `/actuator/health` response does not prove worker progress, model quota or GitHub access. Inspect the affected run and the safe error code for those failures.
