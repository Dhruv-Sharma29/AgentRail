package com.agentflow.execution;

import com.agentflow.agent.LivePlanner;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="agentflow.planner.provider=openai") @AutoConfigureMockMvc @ActiveProfiles("test")
class LivePlanningApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ExecutionService executions;
    @Autowired PlanningCoordinator coordinator;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean LivePlanner live;
    @MockitoBean Clock clock;
    static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    static final String PROPOSAL = "{\"toolCalls\":[{\"tool\":\"classify_issue\",\"arguments\":{\"subject\":\"Cannot log in\",\"body\":\"Password reset failed\"}}]}";

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(live.propose(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new LivePlanner.Result(PROPOSAL, 123);
        });
    }
    @Test void commitsIntentBeforeNetworkAndResultBeforeNextStep() throws Exception {
        UUID run = start(); executions.advance(run);
        coordinator.advance(run);
        var trace = trace(run);
        assertThat(trace.path("mode").asText()).isEqualTo(expectedMode());
        assertThat(trace.path("plannerCalls").asInt()).isEqualTo(1);
        assertThat(trace.path("plannerTokens").asInt()).isEqualTo(123);
        assertThat(trace.path("reservedPlannerTokens").asInt()).isEqualTo(18000);
        assertThat(trace.path("steps").get(1).path("planningState").asText()).isEqualTo("RESOLVED");
        assertThat(trace.path("context").path("classification").path("category").asText()).isEqualTo("ACCOUNT");
        executions.advance(run);
        assertThat(trace(run).path("status").asText()).isEqualTo("SUCCEEDED");
    }
    String expectedMode() { return "OPENAI_PLANNER"; }
    @Test void duplicateClaimsAndDuplicateResultsDoNotSpendOrExecuteAgain() throws Exception {
        UUID run = start(); executions.advance(run);
        var job = executions.claimPlanning(run).orElseThrow();
        assertThat(job.provider()).isEqualTo(expectedMode().equals("GEMINI_PLANNER") ? "gemini" : "openai");
        assertThat(executions.claimPlanning(run)).isEmpty();
        executions.completePlanning(job, new LivePlanner.Result(PROPOSAL, 123), null);
        executions.completePlanning(job, new LivePlanner.Result(PROPOSAL, 123), null);
        assertThat(trace(run).path("plannerCalls").asInt()).isEqualTo(1);
        assertThat(trace(run).path("toolCalls").asInt()).isEqualTo(1);
    }
    @Test void cancellationDoesNotWaitForProviderAndDiscardsItsLateResult() throws Exception {
        UUID run = start(); executions.advance(run);
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { started.countDown(); assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); return new LivePlanner.Result(PROPOSAL,123); }).when(live).propose(any());
        try (var pool = Executors.newSingleThreadExecutor()) {
            var task = pool.submit(() -> coordinator.advance(run));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                mvc.perform(post("/api/runs/"+run+"/cancel").with(user("alice")).with(csrf())).andExpect(status().isOk());
            } finally { release.countDown(); }
            task.get(5, TimeUnit.SECONDS);
        }
        assertThat(trace(run).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(trace(run).path("toolCalls").asInt()).isZero();
        assertThat(trace(run).path("reservedPlannerTokens").asInt()).isEqualTo(18000);
    }
    @Test void restartWithUnknownOutcomeExpiresWithoutResending() throws Exception {
        UUID run = start(); executions.advance(run);
        executions.claimPlanning(run).orElseThrow();
        when(clock.instant()).thenReturn(NOW.plusSeconds(36));
        coordinator.advance(run);
        assertFailed(run,"PLANNING_OUTCOME_UNKNOWN"); verifyNoInteractions(live);
    }
    @Test void lateResultFailsClosed() throws Exception {
        UUID run = start(); executions.advance(run); var job = executions.claimPlanning(run).orElseThrow();
        when(clock.instant()).thenReturn(NOW.plusSeconds(36));
        executions.completePlanning(job,new LivePlanner.Result(PROPOSAL,123),null);
        assertFailed(run,"PLANNING_OUTCOME_UNKNOWN");
    }
    @Test void providerExceptionIsRedactedAndNeverRetried() throws Exception {
        doThrow(new IllegalStateException("secret-provider-response")).when(live).propose(any());
        UUID run = start(); executions.advance(run); coordinator.advance(run); coordinator.advance(run);
        assertFailed(run,"PLANNER_FAILURE");
        assertThat(trace(run).toString()).doesNotContain("secret-provider-response");
        verify(live,times(1)).propose(any());
    }
    @Test void maliciousModelProposalCannotExecuteTools() throws Exception {
        doReturn(new LivePlanner.Result("{\"toolCalls\":[{\"tool\":\"create_ticket\",\"arguments\":{\"approved\":true}}]}",123)).when(live).propose(any());
        UUID run = start(); executions.advance(run); coordinator.advance(run);
        assertFailed(run,"UNKNOWN_TOOL"); assertThat(trace(run).path("steps").get(1).path("proposal").isNull()).isTrue();
    }
    @Test void roleRevokedDuringNetworkRequestIsRechecked() throws Exception {
        UUID run = start(); executions.advance(run); var job = executions.claimPlanning(run).orElseThrow();
        jdbc.update("update users set role='GUEST' where username='alice'");
        try { executions.completePlanning(job,new LivePlanner.Result(PROPOSAL,123),null); assertFailed(run,"TOOL_ROLE_DENIED"); }
        finally { jdbc.update("update users set role='USER' where username='alice'"); }
    }
    @Test void invalidReportedUsageFailsBeforeTools() throws Exception {
        doReturn(new LivePlanner.Result(PROPOSAL,18001)).when(live).propose(any());
        UUID run = start(); executions.advance(run); coordinator.advance(run); assertFailed(run,"PLANNER_TOKEN_LIMIT");
    }
    @Test void concurrentClaimsDispatchAtMostOneRequest() throws Exception {
        UUID run=start(); executions.advance(run); var gate=new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> claim=() -> { gate.await(5,TimeUnit.SECONDS); return executions.claimPlanning(run).isPresent(); };
            var first=pool.submit(claim); var second=pool.submit(claim); gate.countDown();
            assertThat(List.of(first.get(5,TimeUnit.SECONDS),second.get(5,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(trace(run).path("plannerCalls").asInt()).isEqualTo(1);
    }
    @Test void rolledBackResultRetainsIntentAndReservationWithoutRedispatch() throws Exception {
        UUID run=start(); executions.advance(run); var job=executions.claimPlanning(run).orElseThrow();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            executions.completePlanning(job,new LivePlanner.Result(PROPOSAL,123),null); tx.setRollbackOnly();
        });
        assertThat(trace(run).path("toolCalls").asInt()).isZero();
        assertThat(trace(run).path("plannerTokens").asInt()).isZero();
        assertThat(trace(run).path("steps").get(1).path("planningState").asText()).isEqualTo("REQUESTED");
        when(clock.instant()).thenReturn(NOW.plusSeconds(36)); coordinator.advance(run);
        assertFailed(run,"PLANNING_OUTCOME_UNKNOWN"); verifyNoInteractions(live);
    }
    @Test void revokedRolePreventsPaidDispatch() throws Exception {
        UUID run=start(); executions.advance(run);
        jdbc.update("update users set role='GUEST' where username='alice'");
        try { coordinator.advance(run); assertFailed(run,"TOOL_ROLE_DENIED"); verifyNoInteractions(live); }
        finally { jdbc.update("update users set role='USER' where username='alice'"); }
    }
    @Test void exhaustedTokenReservationPreventsDispatch() throws Exception {
        UUID run=start(); executions.advance(run);
        jdbc.update("update workflow_runs set reserved_planner_tokens=90000 where id=?",run);
        coordinator.advance(run); assertFailed(run,"PLANNER_TOKEN_LIMIT"); verifyNoInteractions(live);
    }
    @Test void intentDeadlineCannotExtendTheRunDeadline() throws Exception {
        UUID run=start(); executions.advance(run);
        when(clock.instant()).thenReturn(NOW.plusSeconds(119));
        var job=executions.claimPlanning(run).orElseThrow();
        assertThat(job.deadline()).isEqualTo(NOW.plusSeconds(120));
        when(clock.instant()).thenReturn(NOW.plusSeconds(121));
        executions.completePlanning(job,new LivePlanner.Result(PROPOSAL,123),null);
        assertFailed(run,"RUN_DEADLINE_EXCEEDED");
    }
    @Test void livePlannerCannotResumeHumanPauseAndApprovalWaitPreservesActiveBudget() throws Exception {
        when(live.propose(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            LivePlanner.Job job=call.getArgument(0);
            String proposal=job.request().goal()==com.agentflow.workflow.WorkflowDefinition.AgentGoal.CLASSIFY_ISSUE ? PROPOSAL
                    : "{\"toolCalls\":[{\"tool\":\"search_documentation\",\"arguments\":{\"category\":\"ACCOUNT\"}}]}";
            return new LivePlanner.Result(proposal,123);
        });
        UUID run=start(json.readTree(LocalRetryApiTest.APPROVAL_DEFINITION));
        for(int i=0;i<4;i++) coordinator.advance(run);
        var waiting=trace(run);
        assertThat(waiting.path("mode").asText()).isEqualTo(expectedMode());
        assertThat(waiting.path("status").asText()).isEqualTo("WAITING_FOR_APPROVAL");
        when(clock.instant()).thenReturn(NOW.plusSeconds(600));
        coordinator.advance(run); assertThat(executions.claimPlanning(run)).isEmpty();
        assertThat(trace(run)).isEqualTo(waiting);
        var approval=waiting.path("approvals").get(0);
        mvc.perform(post("/api/approvals/"+approval.path("id").asText()+"/decision").with(user("alice")).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("decision","APPROVE","payloadDigest",approval.path("payloadDigest").asText()))))
                .andExpect(status().isOk());
        coordinator.advance(run);
        assertThat(trace(run).path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(trace(run).path("plannerCalls").asInt()).isEqualTo(2);
        assertThat(trace(run).path("toolCalls").asInt()).isEqualTo(2);
        assertThat(trace(run).path("context").path("result").path("externalTicketCreated").asBoolean()).isFalse();
        verify(live,times(2)).propose(any());
    }
    private void assertFailed(UUID run,String code) throws Exception {
        var trace=trace(run); assertThat(trace.path("status").asText()).isEqualTo("FAILED");
        assertThat(trace.path("errorCode").asText()).isEqualTo(code); assertThat(trace.path("toolCalls").asInt()).isZero();
    }
    private JsonNode trace(UUID run) throws Exception {
        return json.readTree(mvc.perform(get("/api/runs/"+run).with(user("alice"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private UUID start() throws Exception {
        var definition=json.readTree("{\"nodes\":[{\"id\":\"receive\",\"type\":\"TRIGGER\",\"label\":\"Receive\"},{\"id\":\"agent\",\"type\":\"AI_STEP\",\"label\":\"Propose\",\"agent\":{\"goal\":\"CLASSIFY_ISSUE\",\"allowedTools\":[\"classify_issue\"],\"maxToolCalls\":1}},{\"id\":\"record\",\"type\":\"ACTION\",\"label\":\"Record\",\"operation\":\"SEARCH_DOCUMENTATION\"}],\"edges\":[{\"source\":\"receive\",\"target\":\"agent\"},{\"source\":\"agent\",\"target\":\"record\"}]}");
        return start(definition);
    }
    private UUID start(JsonNode definition) throws Exception {
        var created=mvc.perform(post("/api/workflows").with(user("alice")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("name","Live intake","description","Synthetic data","definition",definition)))).andExpect(status().isCreated()).andReturn();
        String workflow=json.readTree(created.getResponse().getContentAsString()).path("id").asText();
        var started=mvc.perform(post("/api/workflows/"+workflow+"/runs").with(user("alice")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("versionNumber",1,"idempotencyKey","live","input",Map.of("subject","Cannot log in","body","Password reset failed"))))).andExpect(status().isAccepted()).andReturn();
        return UUID.fromString(json.readTree(started.getResponse().getContentAsString()).path("id").asText());
    }
}
