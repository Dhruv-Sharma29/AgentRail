package com.agentflow.workflow;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the same HTTP, ownership, versioning, and race contract against real PostgreSQL. */
@Testcontainers
class PostgreSqlWorkflowIT extends WorkflowApiTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
}
