# 08 — Testing

Use the smallest check that exercises a change, then run integration and delivery checks when their boundaries are affected. Live credentials are not required for the automated suites.

## Backend and frontend checks

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

Each restart check uses its own PostgreSQL container and API port. It kills its own Java process after a committed checkpoint, starts a fresh process, and verifies the remaining steps execute once. Agent mode also checks the persisted proposal and usage counters. It cleans up its processes and container. Approval mode kills the process while waiting for review, checks that the same frozen payload remains paused, and resumes only after an explicit test decision. The CI workflow is configured to run verification and all three restart checks.

Tests use an explicitly configured Mockito Java agent, avoiding dynamic self-attachment on newer JDKs. CI runs verify on Java 21 with Docker. H2 tests do not establish PostgreSQL compatibility.

## Container acceptance

With Docker running, execute from the repository root:

```sh
python3 scripts/test_local_setup.py
python3 scripts/compose-test.py --build --browser
```

Install frontend dependencies and Playwright Chromium first, as above. The harness creates a disposable Compose project, random port, temporary credentials and a separate database volume. It checks the deployed authentication/CSRF boundary, exact approvals, API replacement, backup/restore and stack recreation, then runs the browser suite. It removes only its own resources. Generated media and reports live in ignored `artifacts/` and `frontend/test-results/`.

## Choose the right level

| Change | Relevant evidence |
| --- | --- |
| Pure policy or validation | Focused JUnit tests, including denied input |
| Persistence, locking or recovery | PostgreSQL integration tests and the relevant restart scenario |
| Frontend behavior | Vitest, production build, and relevant Playwright scenario |
| Proxy, images or deployment | Disposable Compose acceptance and dependency/image review |
| Documentation | Commands compared with configuration; local links, examples and formatting checked |

`./mvnw verify` uses Maven Failsafe for PostgreSQL integration tests in addition to Surefire tests. A successful H2 test run alone does not prove production-database behavior. Real GitHub writes and paid provider requests are excluded from automated checks; their adapters are exercised with controlled responses.

## CI and recorded results

The [GitHub Actions workflow](../.github/workflows/backend.yml) defines backend verification/restart, frontend, and container delivery jobs. Java 21 and Node 24 are used in CI. Failed tests retain reports or browser traces; the delivery job uploads available artifacts with seven-day retention.

A workflow definition is not evidence of a hosted successful run. Earlier test counts and scans are not a fresh verification of later edits.

Only claim a complete delivery pass after the full container/browser command finishes successfully. Partial or interrupted runs are incomplete evidence.
