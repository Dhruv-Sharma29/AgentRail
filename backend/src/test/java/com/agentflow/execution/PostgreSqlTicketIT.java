package com.agentflow.execution;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
@Testcontainers
class PostgreSqlTicketIT extends TicketExecutionApiTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
}
