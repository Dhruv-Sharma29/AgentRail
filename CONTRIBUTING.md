# Contributing to AgentRail

[Documentation](docs/README.md) · [Architecture](docs/03-architecture.md) · [Testing](docs/08-testing.md)

Build small, working increments that preserve the central invariant: **AI proposes actions; Java validates, authorizes, and controls execution.** Read the repository's [development instructions](AGENTS.md) before making changes.

## Development workflow

1. Describe the behavior, scope and acceptance criteria for one change.
2. Inspect the relevant module and existing tests. Keep unrelated edits intact.
3. Write behavior tests before changing execution, policy, approval or recovery logic.
4. Implement the smallest complete slice, including database migration and API/UI changes when needed.
5. Run focused checks, then the integration or browser checks for the affected boundary.
6. Review correctness, authorization, error handling and recovery. Update the relevant documentation.

Documentation-only changes need accurate examples and link checks rather than new runtime tests. Do not report a command as passed unless it completed successfully for the relevant revision.

## Repository map

| Path | Purpose |
| --- | --- |
| `backend/` | Spring Boot application, Flyway migrations, Java tests and Maven wrapper |
| `frontend/` | React Console, component tests and Playwright scenarios |
| `examples/` | Workflow creation request bodies |
| `scripts/` | Demo, recovery, setup, container acceptance and recording helpers |
| `docs/` | User guides, API contract, architecture and operations |
| `docs/media/` | README demo assets |
| `.github/workflows/` | CI configuration |
| `.agents/skills/` | Project development guidance |

## Backend changes

Keep ownership checks in Java using the authenticated principal. Validate a saved version before run creation, and use explicit state transitions with persisted checkpoints. External calls must run outside database transactions using the intent/result pattern. Never add blanket retries to side-effecting or uncertain operations.

Use a new Flyway migration for schema changes. Hibernate validates the schema; it does not manage production DDL. Preserve historical runs and approvals. Update the API contract when adding fields, limits or lifecycle behavior. Test locking, rollback and restart behavior on PostgreSQL when changing persistence guarantees.

## Frontend changes

Use the existing Console theme and semantic tokens in `frontend/src/styles.css`. Reusable UI components live in `frontend/src/components/ui`; `@/` maps to `frontend/src`. Keep keyboard focus, reduced-motion behavior and readable status text intact.

Use the existing API/session layer and strict workflow DTO conversion. React Flow internals must not enter saved definitions. The UI can explain policy and gather a decision; it cannot authorize an action. Treat stale responses, version conflicts and expired approvals as normal states requiring explicit handling.

## Review checklist

- Acceptance behavior and failure paths are covered by meaningful tests.
- Ownership, CSRF, current roles and exact approval payload checks remain enforced.
- No secret, raw provider exception or unnecessary user content enters logs or fixtures.
- Retry, cancellation and restart behavior are explicit; external uncertainty is preserved.
- Public API/configuration changes have matching documentation and examples.
- Relevant checks and any remaining limitations are recorded clearly in the change description.

Use synthetic inputs and mocked provider/GitHub responses in automated tests. Real issue creation and live model use are separate, explicitly configured demonstrations. Keep `.env`, `.env.local`, database files, backups and generated test artifacts out of commits.
