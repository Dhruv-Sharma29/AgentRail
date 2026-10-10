package com.agentflow.execution;

import com.agentflow.auth.*;
import com.agentflow.tools.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"agentflow.github.enabled=true", "agentflow.github.token=synthetic-test-token", "agentflow.github.repository=Dhruv-Sharma29/test"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class TicketExecutionApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ExecutionService executions;
    @Autowired PlanningCoordinator coordinator;
    @Autowired TicketExecutionService tickets;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GitHubTicketClient client;
    @MockitoBean Clock clock;
    static final Instant NOW=Instant.parse("2026-10-10T00:00:00Z");
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        if(users.findByUsername("bob").isEmpty()) users.save(new AppUser("bob","unused-test-hash"));
        jdbc.update("UPDATE users SET role='USER' WHERE username='alice'");
        when(client.create(any(),any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new GitHubTicketClient.Result(42,"https://github.com/Dhruv-Sharma29/test/issues/42");
        });
    }
    @AfterEach void restore() { jdbc.update("UPDATE users SET role='USER' WHERE username='alice'"); }
    @Test void freezesExactTicketAndOnlyDispatchesOnceAfterApproval() throws Exception {
        var run=pause(); var a=run.path("approvals").get(0); var args=a.path("payload").path("ticket");
        assertThat(args.path("repository").asText()).isEqualTo("Dhruv-Sharma29/test");
        assertThat(args.path("title").asText()).isEqualTo("Cannot log in");
        assertThat(args.path("body").asText()).contains("Password reset failed", "<!-- agentflow-ticket:");
        coordinator.advance(id(run)); verifyNoInteractions(client);
        decide(a,"APPROVE"); coordinator.advance(id(run)); coordinator.advance(id(run));
        var done=trace(id(run));
        assertThat(done.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(done.path("tickets").get(0).path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(done.path("context").path("result").path("externalTicketCreated").asBoolean()).isTrue();
        assertThat(done.path("steps").get(4).path("attempts").asInt()).isEqualTo(1);
        verify(client,times(1)).create(any(),any());
    }
    @Test void rejectionAndCancellationNeverDispatch() throws Exception {
        var rejected=pause(); decide(rejected.path("approvals").get(0),"REJECT"); coordinator.advance(id(rejected));
        var cancelled=pause(); decide(cancelled.path("approvals").get(0),"APPROVE");
        mvc.perform(post("/api/runs/"+id(cancelled)+"/cancel").with(user("alice")).with(csrf())).andExpect(status().isOk());
        coordinator.advance(id(cancelled)); verifyNoInteractions(client);
    }
    @Test void revalidatesRoleAndFrozenSettingsBeforeDispatch() throws Exception {
        var changed=pause(); decide(changed.path("approvals").get(0),"APPROVE");
        jdbc.update("UPDATE node_executions SET ticket_settings=? WHERE run_id=? AND node_id='ticket'", "{\"repository\":\"attacker/repo\"}", id(changed));
        coordinator.advance(id(changed)); assertThat(trace(id(changed)).path("status").asText()).isEqualTo("FAILED");
        var revoked=pause(); decide(revoked.path("approvals").get(0),"APPROVE");
        jdbc.update("UPDATE users SET role='VIEWER' WHERE username='alice'"); coordinator.advance(id(revoked));
        assertThat(trace(id(revoked)).path("errorCode").asText()).isEqualTo("TOOL_ROLE_DENIED"); verifyNoInteractions(client);
    }
    @Test void unknownRemoteOutcomeIsNotRetriedAndCanOnlyBeReconciledByRead() throws Exception {
        when(client.create(any(),any())).thenThrow(new GitHubTicketClient.Failure("TICKET_OUTCOME_UNKNOWN"));
        var run=pause(); decide(run.path("approvals").get(0),"APPROVE");
        coordinator.advance(id(run)); coordinator.advance(id(run));
        assertThat(trace(id(run)).path("errorCode").asText()).isEqualTo("TICKET_OUTCOME_UNKNOWN");
        when(client.find(any(),eq(42))).thenReturn(new GitHubTicketClient.Result(42,"https://github.com/Dhruv-Sharma29/test/issues/42"));
        mvc.perform(post("/api/runs/"+id(run)+"/ticket/reconcile").with(user("alice")).with(csrf()).contentType("application/json").content("{\"issueNumber\":42}"))
                .andExpect(status().isOk());
        assertThat(trace(id(run)).path("status").asText()).isEqualTo("SUCCEEDED");
        verify(client,times(1)).create(any(),any()); verify(client,times(1)).find(any(),eq(42));
    }
    @Test void persistedClaimSurvivesWorkerLossWithoutAnotherPost() throws Exception {
        var run=pause(); decide(run.path("approvals").get(0),"APPROVE");
        var claim=tickets.claim(id(run)); assertThat(claim.job()).isNotNull();
        coordinator.advance(id(run)); verifyNoInteractions(client);
        when(clock.instant()).thenReturn(NOW.plusSeconds(21)); coordinator.advance(id(run));
        assertThat(trace(id(run)).path("tickets").get(0).path("status").asText()).isEqualTo("UNKNOWN");
        coordinator.advance(id(run)); verifyNoInteractions(client);
    }
    @Test void competingWorkersDispatchOneIntentAndCancellationKeepsLateReceipt() throws Exception {
        var run=pause(); decide(run.path("approvals").get(0),"APPROVE");
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        when(client.create(any(),any())).thenAnswer(call -> { entered.countDown(); release.await(10,TimeUnit.SECONDS);
            return new GitHubTicketClient.Result(42,"https://github.com/Dhruv-Sharma29/test/issues/42"); });
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> coordinator.advance(id(run))); assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            pool.submit(() -> coordinator.advance(id(run))).get(5,TimeUnit.SECONDS);
            mvc.perform(post("/api/runs/"+id(run)+"/cancel").with(user("alice")).with(csrf())).andExpect(status().isOk());
            release.countDown(); first.get(10,TimeUnit.SECONDS);
        }
        var done=trace(id(run)); assertThat(done.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(done.path("tickets").get(0).path("status").asText()).isEqualTo("SUCCEEDED");
        verify(client,times(1)).create(any(),any());
    }
    @Test void reconcileEnforcesOwnerCsrfAndRole() throws Exception {
        var run=pause(); decide(run.path("approvals").get(0),"APPROVE"); tickets.claim(id(run));
        String path="/api/runs/"+id(run)+"/ticket/reconcile";
        mvc.perform(post(path).with(user("bob")).with(csrf()).contentType("application/json").content("{\"issueNumber\":42}")).andExpect(status().isNotFound());
        mvc.perform(post(path).with(user("alice")).contentType("application/json").content("{\"issueNumber\":42}")).andExpect(status().isForbidden());
        verifyNoInteractions(client);
    }
    @Test void expiryAndAuthenticationFailureNeverRetryCreation() throws Exception {
        var expired=pause();
        when(clock.instant()).thenReturn(NOW.plusSeconds(3601)); coordinator.advance(id(expired));
        assertThat(trace(id(expired)).path("errorCode").asText()).isEqualTo("APPROVAL_EXPIRED");
        verifyNoInteractions(client);
        when(clock.instant()).thenReturn(NOW);
        var run=pause(); decide(run.path("approvals").get(0),"APPROVE");
        when(client.create(any(),any())).thenThrow(new GitHubTicketClient.Failure("TICKET_AUTHENTICATION"));
        coordinator.advance(id(run)); coordinator.advance(id(run));
        assertThat(trace(id(run)).path("tickets").get(0).path("status").asText()).isEqualTo("FAILED");
        verify(client,times(1)).create(any(),any());
    }
    @Test void reconciliationDeniesAChangedDestinationWithoutReadOrCreate() throws Exception {
        var run=pause(); decide(run.path("approvals").get(0),"APPROVE"); tickets.claim(id(run));
        when(clock.instant()).thenReturn(NOW.plusSeconds(21)); coordinator.advance(id(run));
        jdbc.update("UPDATE node_executions SET ticket_settings=? WHERE run_id=? AND node_id='ticket'", "{\"repository\":\"attacker/repo\"}", id(run));
        mvc.perform(post("/api/runs/"+id(run)+"/ticket/reconcile").with(user("alice")).with(csrf()).contentType("application/json").content("{\"issueNumber\":42}")).andExpect(status().isConflict());
        verifyNoInteractions(client);
    }
    @Test void databaseRequiresACompleteSuccessReceipt() throws Exception {
        var run=pause(); decide(run.path("approvals").get(0),"APPROVE"); tickets.claim(id(run));
        assertThatThrownBy(() -> jdbc.update("UPDATE ticket_executions SET status='SUCCEEDED',issue_url='https://github.com/Dhruv-Sharma29/test/issues/42' WHERE run_id=?",id(run)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void connectorReadinessRequiresLoginAndContainsNoToken() throws Exception {
        mvc.perform(get("/api/tools/github")).andExpect(status().isUnauthorized());
        var response=mvc.perform(get("/api/tools/github").with(user("alice"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(response).size()).isEqualTo(2);
        assertThat(response).doesNotContain("synthetic-test-token","token");
        assertThat(json.readTree(response).path("ready").asBoolean()).isTrue();
    }
    UUID id(JsonNode run) { return UUID.fromString(run.path("id").asText()); }
    JsonNode trace(UUID run) throws Exception {
        return json.readTree(mvc.perform(get("/api/runs/"+run).with(user("alice"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    void decide(JsonNode approval,String decision) throws Exception {
        mvc.perform(post("/api/approvals/"+approval.path("id").asText()+"/decision").with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("decision",decision,"payloadDigest",approval.path("payloadDigest").asText())))).andExpect(status().isOk());
    }
    JsonNode pause() throws Exception {
        var definition=json.readTree("""
        {"nodes":[{"id":"receive","type":"TRIGGER","label":"Receive"},
        {"id":"classify","type":"ACTION","label":"Classify","operation":"CLASSIFY_ISSUE"},
        {"id":"search","type":"ACTION","label":"Search","operation":"SEARCH_DOCUMENTATION"},
        {"id":"review","type":"APPROVAL","label":"Review","approval":{"timeoutSeconds":3600}},
        {"id":"ticket","type":"ACTION","label":"Create ticket","operation":"CREATE_TICKET","ticket":{"repository":"Dhruv-Sharma29/test"}}],
        "edges":[{"source":"receive","target":"classify"},{"source":"classify","target":"search"},{"source":"search","target":"review"},{"source":"review","target":"ticket"}]}
        """);
        var workflow=json.readTree(mvc.perform(post("/api/workflows").with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("name","Ticket test","description","Approved ticket","definition",definition))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var run=json.readTree(mvc.perform(post("/api/workflows/"+workflow.path("id").asText()+"/runs").with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("versionNumber",1,"idempotencyKey",UUID.randomUUID().toString(),"input",Map.of("subject","Cannot log in","body","Password reset failed")))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        for(int i=0;i<4;i++) coordinator.advance(id(run)); return trace(id(run));
    }
}
