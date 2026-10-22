# 07 — Operations

## Start and inspect

Run from the repository root with Docker Desktop running:

```sh
python3 scripts/init-local.py
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
curl --fail http://127.0.0.1:8090/actuator/health
```

Read the private `.env` for the bootstrap username/password. The generated username is `admin`. Never paste the file into chat or commit it. Docker operators can inspect container environment credentials; this is a local setup, not a managed secret store. The first cold build downloads Java and Node dependencies and may take several minutes. Subsequent builds reuse layers.

The frontend health check checks static serving at `/healthz`; `/actuator/health` passes through to Java and its database health check. An `UP` response establishes service/database availability, not correctness of every workflow or model availability. Compose waits for database and API health before starting the web service. [Docker startup ordering](https://docs.docker.com/compose/how-tos/startup-order/).

Use `docker compose logs --tail=100 backend frontend postgres` for bounded diagnostic logs. Proxy logs contain the request method and path, not query strings or bodies. Workflow input/output and decision payloads are persisted application data accessible through owner-scoped APIs; treat backups as sensitive. Avoid logging full API requests, environment dumps or provider headers.

Change `WEB_PORT` in `.env` if 8090 is occupied. Visit the new port after `docker compose up -d --wait`. Only `127.0.0.1:WEB_PORT` is published. The API and database have no host port. Development Vite on 5173 and Java on 8080 use a separate H2 database and separate public development credentials. Docker uses `AGENTFLOW_SESSION` instead of the development `JSESSIONID`; cookie scope does not include a port, so distinct names prevent the two setups from replacing each other's session cookie.

## Monitor owner activity

Open **Operations** in the signed-in Console. Counts refresh every 15 seconds while the view is mounted, with a manual refresh button and last-successful-update timestamp. Failures retain the previous snapshot with an explicit stale label; initial failures show an error rather than empty counts. Review pending decisions in **Approvals** and inspect failed runs through their workflow histories. UNKNOWN ticket outcomes require inspection of the original trace and explicit issue-number reconciliation; do not start another run as a retry.

The dashboard is an owner view over durable state, not a global administrator dashboard or alerting service. Scheduled retries count only active run queues; pending approvals include overdue requests until the worker records expiry. Sending/unknown tickets remain visible after cancellation. All run counts use retained history. No input, secret, raw provider error or approval payload appears in the aggregate endpoint, and no execution mutation is performed. A new owner/status index supports the query; retention and large-data load testing remain future work.

For infrastructure availability use `/actuator/health`; business counts do not establish worker liveness, model quota or GitHub permission. Automated notifications, latency/error-rate time series and external monitoring remain separate work. Do not expose actuator configuration or environment endpoints publicly.

## Stop, restart and preserve history

```sh
docker compose down
docker compose up -d --wait --wait-timeout 180
```

`down` preserves the `postgres-data` volume. Keep the same directory/project name when restarting so Compose selects the same volume. Do not add `--volumes` or use a volume prune on a database you want to keep. Removing the named volume destroys its data.

For an API image/config update:

```sh
docker compose build backend
docker compose up -d --no-deps --force-recreate --wait --wait-timeout 180 backend
```

The unchanged web proxy refreshes the backend address through Docker DNS. Its upstream uses a shared zone and resolution; Nginx supports this in the pinned stable release. [Nginx upstream resolution](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#server). Temporary requests during replacement may fail; the proxy does not automatically retry mutations. Sign in again after Java restart: HTTP sessions are process-local. Persisted workflows, versions, runs, approval payloads and audits remain in PostgreSQL. A pending approval remains paused and requires an explicit owner decision. Provider requests with uncertain outcomes follow the engine's fail-closed recovery; they are not automatically resent.

## Back up and verify a restore

These commands use the configured database without printing credentials. Keep backup permissions private and store an encrypted copy outside the laptop for actual disaster recovery.

```sh
umask 077
mkdir -p .backups
docker compose exec -T postgres sh -c 'exec pg_dump -U agentflow -d "$POSTGRES_DB" -Fc' > .backups/agentflow.dump
docker compose exec -T postgres createdb -U agentflow agentflow_restore
docker compose exec -T postgres pg_restore -U agentflow -d agentflow_restore --exit-on-error --no-owner --no-acl < .backups/agentflow.dump
docker compose exec -T postgres psql -U agentflow -d agentflow_restore -c 'SELECT status, count(*) FROM workflow_runs GROUP BY status;'
```

The restore target is a new database; `createdb` fails if that name already exists. Choose another new name rather than overwriting an existing database. A dump contains account hashes, workflow definitions, issue content and audits. `.backups/` is ignored by Git. The command's standard-output redirection creates the dump before `pg_dump` finishes; check the exit status and successful restore before treating it as valid. Preserve older validated backups separately instead of overwriting your only copy.

To switch to the validated restore, stop the API, set `DATABASE_NAME=agentflow_restore` in `.env`, then run `docker compose up -d --wait --wait-timeout 180`. The restored database already contains users; use their original credentials. Keep the original database until you have validated the restored app. Restoring onto a new machine also requires your private configuration and a compatible PostgreSQL 17 image. The disposable acceptance harness verifies dump/restore and pause/resume on the same Docker host; it does not establish off-host recovery or a retention policy.

Changing `.env` passwords does not rotate existing bootstrap users or existing PostgreSQL role passwords. PostgreSQL initialization variables seed an empty volume only. Credential rotation requires explicit application/database administration and coordinated configuration updates; a self-service rotation API is not implemented.

## Deployment boundary

This Compose file is a local deployment with a compiled frontend and persistent database. It defaults to deterministic planning and never mounts the developer's `.env.local`. API/web processes run as non-root with read-only filesystems, bounded memory, dropped capabilities and temporary writable `/tmp`. PostgreSQL uses its official initialization/privilege handling and its own internal network. Base images are pinned by digest; updating a digest requires rebuild, scan and acceptance checks.

Before remote exposure, configure a trusted TLS ingress and set `SESSION_COOKIE_SECURE=true` on the backend through an explicit Compose override. Keep database/API internal, preserve the same-origin API routes, set HSTS at the TLS edge, and verify authentication and approval flows over HTTPS. Do not publish the current loopback HTTP endpoint remotely. The repository does not include a domain, certificate or hosted deployment.

Registration, password rotation, login throttling, centralized authentication/administrative audits, independent approvers, encrypted backup storage/retention and broader accessibility/browser coverage remain separate work. The opt-in GitHub connector supports only its server-owned repository and exact approved issue; arbitrary external tools remain unsupported. The current approval role is the workflow owner with USER; it does not demonstrate separation of duties.
