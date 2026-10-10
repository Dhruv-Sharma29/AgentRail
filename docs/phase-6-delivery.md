# Phase 6 — container delivery

## Scope and acceptance criteria

Deliver one compiled frontend/Java API origin with persistent PostgreSQL, without weakening the Java execution boundary. Preserve the existing local development setup. Public TLS hosting, external ticket effects and additional roles are separate increments.

| Slice | Acceptance criteria |
| --- | --- |
| Private local configuration | Generate unique database/account credentials with mode 0600; never overwrite existing configuration or print secrets. |
| Container boundary | Only loopback web port published; database/API internal; image digests pinned; API/web non-root and read-only; health-based startup; same-origin cookies/CSRF. |
| Recovery | A frozen pending approval survives API replacement and full stack recreation; the web proxy recovers without replacement; repeated identical approval produces one decision/effect. |
| Backup | Restore a custom-format PostgreSQL dump into a separate database and verify the waiting run exists. |
| Browser delivery | Seven existing Chromium cases run against compiled static assets and PostgreSQL; retain successful demo video and execution screenshot. |
| Delivery checks | Frontend formatting/unit/build; private setup tests; dependency review; operational docs and a CI job for the deployed stack. |

The acceptance harness creates its own randomly named Compose project, temporary credentials, loopback port and database volume. Only that test project is torn down with `--volumes`. The synthetic fixture is explicitly approved by the test; user-owned pending requests are never decided by the harness.

`python3 scripts/compose-test.py --check-config` checks the public port boundary without starting containers. Add `--build --browser` for image builds, recovery checks and browser coverage. Generated demo artifacts are local files under ignored `artifacts/`; CI retains them as run artifacts.

The deterministic agent fixture still proposes two registered tools. Java validates and controls both calls, then freezes the result action for human approval. This delivery adds no arbitrary tool registration, shell/SQL tools, automatic approvals, paid model dispatch or permission bypass.

See [verification](verification.md), [operations](operations.md), [security review](security-review.md) and the [demo guide](demo-script.md) for evidence, startup and limitations. The image review identifies remaining Spring/Go-helper advisories; they are not suppressed or called fixed. A passing local suite is not a claim that a hosted CI run or public production deployment has occurred.
