# Foundation verification

Verified locally on 2026-10-09 with Oracle JDK 25.0.1; compilation targets Java 21. CI is configured for Temurin 21.

## Successful checks

- Skill-creator's bundled validator passed all 11 installed/project skills. Referenced Markdown files were checked for existence.
- `./mvnw test`: 27 tests passed, zero failures, errors, or skips. Includes 14 graph tests and 13 HTTP/auth/workflow tests.
- `./mvnw package`: succeeded and produced the executable Spring Boot jar.
- `python3 scripts/smoke-test.py`: passed against the packaged application on loopback, using actual login credentials, session cookies, and CSRF tokens. Covers create, update, stale-edit conflict, history, archive, logout, and unauthenticated access.
- `docker compose config --quiet`: passed with validation-only environment values; no containers were started by this check.
- Maven distribution downloaded from Maven Central, verified against SHA-512, and its SHA-256 pinned in wrapper configuration.

Graph tests first recorded 12 failures against an empty validator before implementation. HTTP tests recorded 8 expected contract failures before endpoints existed. The test environment needed an explicit Mockito agent because dynamic attachment failed under the Java 25 sandbox; this was fixed before completing the HTTP red/green cycle.

## Incomplete checks

`./mvnw verify` reached PostgreSQL integration testing and failed while obtaining a container because the Docker daemon is stopped/unavailable. It did not exercise PostgreSQL migrations, schema validation, or locking. H2 results do not substitute for those checks. PostgreSqlWorkflowIT repeats the complete HTTP/ownership/version/race contract against real PostgreSQL. CI runs the gate without a silent skip.

Container image builds, a running Compose deployment, and remote deployment have not been verified. No claim of production readiness is made. Phase 1 remains pending PostgreSQL/container verification.

## Separate review pass

Reviewed correctness, readability, boundaries, security, and bounded query behavior after implementation, using the installed review workflow and project agent-security-review skill.

- Identity comes from the security principal. Object reads, edits, archives, and history lookups are owner-scoped; foreign owners see 404. Request-supplied ownership fields are rejected.
- Session login preserves CSRF and session-fixation defenses. Passwords are hashed with BCrypt. Secure cookies default on; public dev credentials are confined to the explicit loopback dev profile.
- Version records are immutable through the API. Updates serialize on the owned row and check expectedVersion. Invalid graph updates leave metadata/version unchanged; concurrent edits create exactly one new version in H2 tests.
- DTOs and persistence entities are separate. Graphs and list sizes are bounded. Errors omit internal traces. Workflow versions use a text column without PostgreSQL large-object/OID mapping.
- Structural exploration used codebase-memory with coverage checks for all 14 reviewed main source files. The graph did not expose complete service call paths; source snippets and direct inspection supplied the boundary evidence. Coverage is a best-effort signal.
- No speculative engine/provider interfaces or permissive tool executor were added. The next increment has explicit contracts in the specification and plan.

Known follow-up requirements: PostgreSQL verification, registration/password rotation, login throttling, execution audit records, approvals, tool authorization, model credentials, durable run recovery, and production ingress. These are described as future work, not existing safeguards.
