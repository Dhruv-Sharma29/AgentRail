package com.agentflow.execution;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class PostgreSqlApprovalIT extends ApprovalApiTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
}
