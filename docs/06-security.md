# 06 — Security model

**AI output is untrusted input. Java owns authorization and execution.** This document describes the implemented trust boundary and its limits. It is not a security certification.

## Trust boundaries

| Boundary | Enforced control |
| --- | --- |
| Browser → API | Session authentication, CSRF on mutations, strict request validation |
| Principal → owned data | Owner-scoped queries; a request cannot choose its owner |
| Saved graph → executable plan | Separate structural and execution validation; immutable version binding |
| Model → tool proposal | Strict JSON parsing, size/call budgets, tool allowlist, typed arguments and context matching |
| Proposal → handler | Java role and policy checks; accepted arguments revalidated before dispatch |
| Human decision → action | Frozen payload and digest, expiry, current role, run lock and exact action recheck |
| Java → GitHub | Opt-in fixed host, one allowed repository, bounded HTTP, committed intent and no automatic resend |

## Authentication and ownership

Users have BCrypt password hashes and server-side sessions. Cookies are HttpOnly and SameSite=Lax. Secure cookies are the default application setting; the explicit local HTTP setups disable that flag. Mutations, including login/logout, require CSRF. The browser refreshes the CSRF token after login and does not store session credentials in local storage.

Workflow, version, run, approval and trace access is owner-scoped. Missing and unowned resources both return 404. Critical execution, approval and reconciliation paths recheck the persisted USER role. The current account model does not provide independent approvers, teams, registration or password rotation.

## Prompt injection and tool permissions

Issue content, documentation content and model output are data. They cannot grant permissions or provide a principal, credential, approval decision or execution callback. AI steps can propose only the registered local classification and search tools, subject to their saved allowlists and Java policy.

Strict parsing rejects unknown fields, duplicate keys and trailing JSON. Context checks prevent a model from substituting a different issue or category. Tool registration is code-controlled; there are no arbitrary shell, SQL, filesystem or user-selected outbound URL tools. The external `create_ticket` action is excluded from AI proposals.

These controls reduce the authority of malicious model output; they do not claim that the model itself is immune to prompt injection. Additional tools or retrieved external content require a fresh boundary review.

## Approval and external effects

An approval authorizes the exact frozen action. Ownership, role, status, deadline and payload digest are checked under the run lock. Rejection, expiry and cancellation prevent subsequent dispatch. The approved payload cannot be replaced by a decision request.

GitHub creation persists an intent before sending a bounded request outside database transactions. A lost response becomes an uncertain outcome, not permission to resend. Explicit reconciliation reads an issue and checks its full approved content and marker. Cancellation after dispatch cannot undo an external action. Run idempotency does not establish exactly-once delivery to an external service.

## Secrets and retained data

Keep credentials in ignored local configuration or a deployment secret system. Provider keys and GitHub tokens never belong in frontend bundles, workflow definitions, screenshots or audit payloads. If a key is exposed, revoke/rotate it at its provider and update the server configuration. Do not publish the old value in an incident report.

Runs and node checkpoints retain submitted issue content. Approval records retain the reviewed action, including ticket content. Owner APIs expose these records, and database backups contain them plus account password hashes. Apply appropriate access, encryption and retention controls to backups. No automated data-retention policy is implemented.

Audit events are ordered, persisted and append-only through the application. They are not cryptographically tamper-evident and do not yet cover all authentication/administrative activity. Safe error codes avoid exposing provider exception bodies or secrets.

## Known dependency findings

A HIGH/CRITICAL image scan (Trivy, 10 October 2026) left these findings open. Rescan before relying on this list.

| Component | Finding | Current exposure |
| --- | --- | --- |
| Spring Framework 6.2.19 (Java image) | [CVE-2026-47884](https://spring.io/security/cve-2026-47884/), [CVE-2026-47890](https://spring.io/security/cve-2026-47890/), [CVE-2026-47892](https://spring.io/security/cve-2026-47892/) | The affected features (XSLT views, SSE view fragments, WebFlux functional routing) are not used. The free fix is Spring 7.0.9, which requires a Spring Boot/Spring AI platform migration. |
| `gosu` in the PostgreSQL image | Go standard-library findings | Startup privilege-switching helper only; it does not serve network traffic. Needs a vendor image with a rebuilt helper. |

The frontend `npm audit` and the web runtime image reported no HIGH/CRITICAL findings at that scan. Jackson, Tomcat and PostgreSQL JDBC are pinned to patched versions in [`backend/pom.xml`](../backend/pom.xml).

## Before public deployment

The included Compose stack is a local deployment. Public hosting requires work beyond publishing its port:

- Resolve the [known dependency findings](#known-dependency-findings) and rescan.
- Configure trusted HTTPS ingress, Secure cookies and appropriate edge headers.
- Separate migration privileges from a restricted runtime database role; the local PostgreSQL role is currently privileged.
- Add account lifecycle controls, credential rotation and login throttling.
- Define backup encryption, retention and off-host restore procedures.
- Decide whether independent reviewers and separation of duties are required.
- Validate load behavior, monitoring and operational recovery for the intended environment.

Report suspected vulnerabilities privately to the repository maintainer through an agreed channel. Avoid public issues containing credentials, customer payloads or exploit details. No dedicated security mailbox or disclosure service is configured in this repository.
