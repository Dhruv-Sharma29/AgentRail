---
name: java-spring-boot
description: Implement and review AgentFlow Java and Spring Boot REST APIs, JPA persistence, transactions, migrations, and module boundaries.
---

# java-spring-boot

Use the pinned Java target, Spring Boot BOM, Maven wrapper, and repository test commands. Check current official documentation when changing dependency versions. Prefer constructor injection and a modular monolith with auth, workflow, execution, agent, tools, approvals, and audit packages.

- Separate HTTP DTOs from entities. Apply Bean Validation at HTTP and tool boundaries and return safe, consistent errors.
- Derive identity from Spring Security, never a request owner ID. Enforce ownership and roles on reads and mutations.
- Use Flyway migrations and Hibernate schema validation. Use UUIDs, UTC timestamps, foreign keys, and database uniqueness constraints.
- Put transactions on application service boundaries; self-invocation bypasses transaction proxies. Test rollback and races where correctness depends on them.
- Bind each run to an immutable workflow version. Avoid lazy entity serialization, N+1 queries, and unbounded lists.
- Do not hold transactions or row locks across model or remote tool calls. Introduce durable intent records and workers when external effects require them.
- Test pure policies with JUnit, auth and HTTP behavior through Spring integration tests, and PostgreSQL-specific behavior with Testcontainers. H2 does not prove PostgreSQL compatibility.

Keep abstractions proportional to the current slice and document exact verified commands and remaining limitations.
