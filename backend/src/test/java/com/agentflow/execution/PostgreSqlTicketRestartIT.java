package com.agentflow.execution;

import com.agentflow.AgentFlowApplication;
import com.agentflow.approvals.ApprovalDtos;
import com.agentflow.tools.GitHubTicketClient;
import com.agentflow.workflow.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import static com.agentflow.workflow.WorkflowDefinition.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tear down all Java beans and connections, then recover the same committed PostgreSQL intent. */
@Testcontainers
class PostgreSqlTicketRestartIT {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    static Instant now=Instant.parse("2026-10-10T00:00:00Z");
    @TestConfiguration(proxyBeanMethods=false)
    static class SyntheticBoundary {
        @Bean @Primary GitHubTicketClient syntheticTicketClient() { return mock(GitHubTicketClient.class); }
        @Bean @Primary Clock restartClock() { return Clock.fixed(now,ZoneOffset.UTC); }
    }
    org.springframework.context.ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(AgentFlowApplication.class,SyntheticBoundary.class).profiles("test").run(
                "--server.port=0","--spring.datasource.url="+postgres.getJdbcUrl(),
                "--spring.datasource.username="+postgres.getUsername(),"--spring.datasource.password="+postgres.getPassword(),
                "--agentflow.github.enabled=true","--agentflow.github.token=synthetic-test-token",
                "--agentflow.github.repository=Dhruv-Sharma29/test");
    }
    @Test void rollbackDoesNotDispatchAndRestartNeverResendsCommittedIntent() {
        UUID runId; String frozen;
        try(var first=start()) {
            var definition=new WorkflowDefinition(List.of(
                    new Node("receive",NodeType.TRIGGER,"Receive"),
                    new Node("classify",NodeType.ACTION,"Classify",Operation.CLASSIFY_ISSUE),
                    new Node("review",NodeType.APPROVAL,"Review",null,null,new ApprovalSettings(3600)),
                    new Node("ticket",NodeType.ACTION,"Create",Operation.CREATE_TICKET,null,null,null,new TicketSettings("Dhruv-Sharma29/test"))),
                    List.of(new Edge("receive","classify"),new Edge("classify","review"),new Edge("review","ticket")));
            var workflow=first.getBean(WorkflowService.class).create("alice",new WorkflowDtos.Create("Restart ticket","",definition));
            var execution=first.getBean(ExecutionService.class);
            runId=execution.start("alice",workflow.id(),new ExecutionDtos.Start(1,UUID.randomUUID().toString(),new ExecutionDtos.IssueInput("Cannot log in","Password reset failed"))).id();
            var coordinator=first.getBean(PlanningCoordinator.class);
            for(int i=0;i<3;i++) coordinator.advance(runId);
            var approval=execution.get("alice",runId).approvals().getFirst(); frozen=approval.payload().toString();
            first.getBean(ApprovalService.class).decide("alice",approval.id(),new ApprovalDtos.Decide(ApprovalDtos.Decision.APPROVE,approval.payloadDigest()));
            var tickets=first.getBean(TicketExecutionService.class);
            var transaction=new TransactionTemplate(first.getBean(PlatformTransactionManager.class));
            UUID captured=runId;
            assertThatThrownBy(() -> transaction.execute(status -> { assertThat(tickets.claim(captured).job()).isNotNull(); throw new IllegalStateException("rollback test"); })).hasMessage("rollback test");
            assertThat(execution.get("alice",runId).tickets()).isEmpty();
            assertThat(execution.get("alice",runId).toolCalls()).isZero();
            assertThat(tickets.claim(runId).job()).isNotNull();
            assertThat(execution.get("alice",runId).tickets().getFirst().status()).isEqualTo("SENDING");
            verifyNoInteractions(first.getBean(GitHubTicketClient.class));
        }
        now=now.plusSeconds(21);
        try(var restarted=start()) {
            var execution=restarted.getBean(ExecutionService.class);
            assertThat(execution.get("alice",runId).approvals().getFirst().payload().toString()).isEqualTo(frozen);
            assertThat(execution.runnable()).contains(runId);
            restarted.getBean(PlanningCoordinator.class).advance(runId);
            restarted.getBean(PlanningCoordinator.class).advance(runId);
            var recovered=execution.get("alice",runId);
            assertThat(recovered.status()).isEqualTo(RunStatus.FAILED);
            assertThat(recovered.errorCode()).isEqualTo("TICKET_OUTCOME_UNKNOWN");
            assertThat(recovered.tickets().getFirst().status()).isEqualTo("UNKNOWN");
            assertThat(recovered.steps().getLast().attempts()).isEqualTo(1);
            verifyNoInteractions(restarted.getBean(GitHubTicketClient.class));
        }
    }
}
