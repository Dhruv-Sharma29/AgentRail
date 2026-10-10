# Delivery security review — 10 October 2026

This is a bounded review of the local container deployment and its dependency boundary, not a penetration test or production certification. Java still owns identity, graph validation, tool permissions, budgets, frozen approvals and effects. Nginx serves static React assets and proxies API requests; it does not make policy decisions.

## Boundary review

- Only the web port is published on loopback. PostgreSQL has an internal data network and no host port; the web container has no database connection or credentials.
- Java authentication, owner checks and CSRF apply through the proxy. Docker and developer sessions use distinct cookie names. Cookies remain HttpOnly/SameSite=Lax; Secure is disabled only for this local HTTP setup.
- The proxy supplies a same-origin CSP, frame denial, no-referrer, nosniff and no-store headers. Inline styles support React Flow; scripts and connections remain same-origin. API mutations are not retried by the proxy. Query strings, request bodies and environment credentials are not in proxy access logs.
- API/web runtime processes are non-root, read-only, memory-bounded, capability-dropped and configured with no-new-privileges. PostgreSQL retains the official image's initialization/privilege handling and durable writable volume. The local API currently uses the PostgreSQL bootstrap database role, which is a superuser; split migration ownership and a restricted runtime role before public deployment.
- Build contexts exclude private environment files, local H2 data, generated results and dependency directories. Copy instructions bring only required source/build/configuration files into stages. Compose does not mount `.env.local` or dispatch paid provider calls. Local credentials are generated with mode 0600; environment injection is inspectable by Docker operators.
- Recovery checks preserve a frozen approval, reject a wrong digest, resume only on an explicit synthetic decision, and reject duplicate effects. Existing PostgreSQL tests cover owner/role enforcement and competing decisions.

## Dependency review and patches

`npm audit` reported zero known frontend vulnerabilities across all severity levels. Trivy scans of exported runtime images checked HIGH/CRITICAL OS and Java/binary findings. The official scanner was pinned to `aquasec/trivy@sha256:af6acf9a6b85dfe389a1941505c0ce9efef52a4719635e1a962f022a3d855daa` (0.75 release). It received only credential-free image archives in a temporary directory, without a Docker socket or source/secret mounts. Docker Scout was unavailable without account login; it was not used to establish results.

The initial Java image had thirteen HIGH/CRITICAL package findings. Compatible patches now explicitly override the Boot 3.5.16 baseline: Jackson BOM 2.21.7, Tomcat 10.1.60 and PostgreSQL JDBC 42.7.14. The web image also pins patched Alpine libexpat 2.8.5-r0, pcre2 10.49-r0 and tiff 4.7.2-r0. These library fixes supplement the pinned base digest and require repository access during build. Supporting sources: [Jackson core advisory](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-p6pp-m3f8-5c89), [Jackson databind advisory](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-wv8q-qhhj-9h54), [Tomcat advisories](https://tomcat.apache.org/security-10.html), [pgJDBC advisories](https://jdbc.postgresql.org/security/).

## Remaining findings and applicability

The Spring Framework 6.2.19 components remain unpatched. These findings are not suppressed or represented as fixed. Vendor conditions were compared with the current source and controller/configuration boundary:

| Advisory | Vendor condition | Current application review |
| --- | --- | --- |
| [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) | XsltView rendering with an implicit view name under a catch-all mapping. | REST controllers return JSON DTOs/ResponseEntity; no XSLT view/resolver or catch-all view mapping is configured. |
| [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) | SSE streams carry view fragments with attacker-controlled content. | Live state uses authenticated JSON polling; no SSE or view fragments are used. |
| [CVE-2026-47892](https://spring.io/security/cve-2026-47892/) | WebFlux functional endpoint header predicates deployed with DispatcherServlet. | Server routing uses annotated MVC REST controllers; no WebFlux functional router is configured. WebFlux is a transitive provider-client dependency. |

The scanner labels these four package occurrences CRITICAL; Spring rates the three advisories MEDIUM, LOW and MEDIUM respectively. This does not erase the vulnerable component versions. The free vendor fix is Spring 7.0.9; Spring 6.2.20 fixes require enterprise support. A coordinated Spring Boot/Spring AI platform migration or a licensed compatible patch is required before public production deployment. Do not force Spring 7 into Boot 3.5's dependency graph. Adding any affected server feature invalidates this applicability assessment.

The pinned PostgreSQL image carries twenty-five HIGH/CRITICAL Go standard-library findings in `/usr/local/bin/gosu`. The report target is that startup privilege-switching binary, not the PostgreSQL database server. [gosu's source](https://github.com/tianon/gosu/blob/1.19/main.go) performs local process/user switching; it does not serve HTTP, parse TLS connections or accept workflow data. The Docker entrypoint invokes it with the fixed postgres user and server command. This limits current exposure, but does not patch the embedded Go version. Track a vendor image with a rebuilt helper before remote deployment; changing the database image requires fresh scans and restore/recovery checks.

The patched-image rescan reports four HIGH/CRITICAL package findings in Java (the three unique Spring advisories above), zero in the web runtime, and twenty-five in the PostgreSQL helper. Full JSON scan reports are retained locally under ignored `artifacts/security/`. Scans are a point-in-time database check, omit lower-severity image findings, and do not prove absence of vulnerabilities or all exploit paths. No suppression policy, VEX assertion or zero-CVE claim was added.

## Follow-up before public deployment

Upgrade the supported Spring platform and PostgreSQL helper image, rescan, restrict the runtime database role, configure trusted HTTPS/Secure cookies, add account lifecycle/rotation and login throttling, and define backup encryption/retention and independent reviewer roles. Public hosting is outside the current local delivery scope. Reassess the threat boundary before adding arbitrary destinations, retrieved external content, remote tools or effect retries.


## Approved GitHub connector review

The new capability is opt-in and constrained to one server-owned repository. Workflow destinations are compared exactly against that setting. Production HTTP uses the fixed HTTPS GitHub API host, never a submitted URL; redirects are disabled and the token is not serialized, logged or returned by readiness. Responses are bounded to 64 KiB, strict about duplicate/trailing JSON, and must match the full approved title/body and exact GitHub issue URL. Pull requests cannot be used for reconciliation. Provider text/errors cannot grant tool permission.

AI step allowlists still exclude `create_ticket`. A final ticket action requires immediate exact-payload approval and current persisted USER role. Run locks serialize approval, cancellation, claim, completion and reconciliation. One committed intent precedes HTTP; network calls hold no database transaction. Lost outcomes do not cause resend. Reconciliation requires owner/session/CSRF and reads an explicit issue in the allowlisted repository. Cancellation after dispatch retains a late receipt instead of claiming to undo an external write. Database checks require complete successful receipts.

Reviewed tests cover policy/destination tampering, role revocation, rejection/expiry, owner and CSRF enforcement, hostile links, malformed/oversized/slow HTTP, competing workers, late cancellation, rollback and complete application restart on PostgreSQL. Tokens and real GitHub issues are excluded from automated checks. Existing public deployment findings and independent-approver limitations remain. Readiness establishes configuration only, not verified remote permissions.
