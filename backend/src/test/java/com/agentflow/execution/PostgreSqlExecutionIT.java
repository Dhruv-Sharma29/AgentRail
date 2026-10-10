package com.agentflow.execution;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The same run ownership, checkpoint, rollback and race contracts against PostgreSQL. */
@Testcontainers
class PostgreSqlExecutionIT extends ExecutionApiTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
}
