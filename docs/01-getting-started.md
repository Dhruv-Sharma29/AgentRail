# 01 — Getting started

Choose standalone development for editing code, or Docker Compose for the compiled app with PostgreSQL. Both run on your machine. Their databases and login credentials are separate.

## Prerequisites

| Requirement | Standalone development | Docker Compose |
| --- | --- | --- |
| Java | JDK 21+ | Built inside the image |
| Node and npm | Node 22.12+; CI uses Node 24 | Built inside the image |
| Python | Python 3 for demo/check scripts | Python 3 for credential initialization |
| Docker | Only for PostgreSQL integration tests | Docker Desktop/Engine with Compose and a running daemon |
| Provider keys | Optional | Not required; deterministic by default |

Open a terminal in your AgentRail checkout. On macOS use `./mvnw`; `mvnw.cmd` is the Windows wrapper. The wrapper downloads Maven and dependencies on first use.

## Option A: standalone development

Start the backend from the repository root:

```sh
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Wait for the Spring Boot startup message. Keep this terminal running. In a second terminal, starting from the repository root:

```sh
cd frontend
npm ci
npm run dev
```

Open [http://127.0.0.1:5173](http://127.0.0.1:5173). Sign in with username `demo` and password `demo-local-only`. These are intentionally public development credentials; the `dev` profile binds the backend to loopback and uses file-backed H2 in `backend/data/`.

The Java entry point is `AgentFlowApplication`; the frontend entry point is `frontend/src/main.tsx`. Use the launch commands above rather than running a source file directly. `./mvnw verify` runs checks and exits; it is not the normal app launch command.

Verify the backend in a third terminal:

```sh
curl --fail http://127.0.0.1:8080/actuator/health
```

An `UP` status confirms backend/database health. It does not verify live provider quota or GitHub permissions. Vite forwards `/api` and `/actuator` to port 8080, preserving session and CSRF protection.

Use Ctrl-C in each server terminal to stop. Restart with the same commands; H2 data is retained. Run only one backend against that database at a time. Restart the backend after backend changes.

## Option B: PostgreSQL and the compiled frontend

With Docker running, execute from the repository root:

```sh
python3 scripts/init-local.py
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
```

The initializer creates a private, ignored `.env` with independent random database/bootstrap passwords and preserves an existing file. Open it locally to read `BOOTSTRAP_USERNAME` and `BOOTSTRAP_PASSWORD`. Do not share its contents. Open [http://127.0.0.1:8090](http://127.0.0.1:8090) and sign in with those credentials.

Only the web port is published on loopback. Nginx serves the app and proxies requests to Java; Java and PostgreSQL have no host ports. Startup downloads and builds dependencies, so the first run can take several minutes.

Stop the stack with:

```sh
docker compose down
```

The named PostgreSQL volume remains. Avoid `--volumes` when keeping your data. Use the same Compose project/directory on restart. Development H2 history is not imported into this database. See [operations](07-operations.md) for backups and safe replacement.

## Complete your first workflow

1. Open **Workflows** and choose **New workflow**.
2. Keep a supported linear support workflow and save it.
3. Choose **Run workflow** and submit a subject and body.
4. Follow the execution trace. If the workflow includes approval, open the request and review its frozen action.
5. Explicitly approve or reject it. Inspect the resulting steps and audit trail.

For reproducible terminal demos against the standalone backend, run from the repository root:

```sh
python3 scripts/smoke-test.py
python3 scripts/execution-demo.py
python3 scripts/execution-demo.py --agent
python3 scripts/approval-demo.py
```

The scripts authenticate to the development server, create sample workflows and verify their behavior. The smoke test archives its sample; execution demos retain history. `--agent` uses the server's configured planner: deterministic by default, live when explicitly configured. The approval script waits for your typed `approve` or `reject`. These demos record a local result and do not create a GitHub issue.

Next: [workspace guide](02-user-guide.md), [optional integrations](05-configuration.md), or [troubleshooting](09-troubleshooting.md).
