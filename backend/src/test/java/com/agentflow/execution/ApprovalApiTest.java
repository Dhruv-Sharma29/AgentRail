package com.agentflow.execution;

import com.agentflow.auth.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ApprovalApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ExecutionService executions;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean Clock clock;
    static final Instant NOW=Instant.parse("2026-10-10T00:00:00Z");
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        if(users.findByUsername("bob").isEmpty()) users.save(new AppUser("bob","unused-test-hash"));
        jdbc.update("UPDATE users SET role='USER' WHERE username='alice'");
    }
    @AfterEach void restoreRole() { jdbc.update("UPDATE users SET role='USER' WHERE username='alice'"); }

    @Test void pausesWithImmutableActionAndCannotAdvanceOrCreateDuplicateApproval() throws Exception {
        var r=pause(); var run=id(r); var approval=r.path("approvals").get(0);
        assertThat(r.path("status").asText()).isEqualTo("WAITING_FOR_APPROVAL");
        assertThat(r.path("steps").get(3).path("status").asText()).isEqualTo("WAITING_FOR_APPROVAL");
        assertThat(r.path("context").has("result")).isFalse();
        assertThat(approval.path("payload").path("operation").asText()).isEqualTo("RECORD_RESULT");
        assertThat(approval.path("payloadDigest").asText()).matches("[0-9a-f]{64}");
        executions.advance(run); executions.advance(run);
        assertThat(trace(run).path("approvals").size()).isEqualTo(1);
        assertThat(trace(run).path("steps").get(4).path("attempts").asInt()).isZero();
    }
    @Test void ownerApprovalResumesOnceAndHumanWaitDoesNotConsumeAgentBudget() throws Exception {
        var r=pause(); var run=id(r); var approval=r.path("approvals").get(0);
        when(clock.instant()).thenReturn(NOW.plusSeconds(600));
        assertThat(decide(approval,"APPROVE",200).path("status").asText()).isEqualTo("APPROVED");
        var events=trace(run).path("events").size(); decide(approval,"APPROVE",200);
        assertThat(trace(run).path("events").size()).isEqualTo(events);
        assertThat(trace(run).path("context").has("result")).isFalse();
        executions.advance(run); executions.advance(run);
        var done=trace(run);
        assertThat(done.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(done.path("steps").get(3).path("attempts").asInt()).isEqualTo(1);
        assertThat(done.path("steps").get(4).path("attempts").asInt()).isEqualTo(1);
        assertThat(done.path("context").path("result").path("subject").asText()).isEqualTo("Cannot log in");
        decide(approval,"REJECT",409);
    }
    @Test void rejectionIsDurableAndIdempotentAndPreventsResult() throws Exception {
        var r=pause(); var a=r.path("approvals").get(0); decide(a,"REJECT",200); decide(a,"REJECT",200);
        executions.advance(id(r)); var done=trace(id(r));
        assertThat(done.path("status").asText()).isEqualTo("FAILED");
        assertThat(done.path("errorCode").asText()).isEqualTo("APPROVAL_REJECTED");
        assertThat(done.path("context").has("result")).isFalse(); decide(a,"APPROVE",409);
    }
    @Test void enforcesOwnerAuthenticationCsrfRoleAndStrictDecisionBody() throws Exception {
        var r=pause(); var a=r.path("approvals").get(0); String path="/api/approvals/"+a.path("id").asText();
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(post(path+"/decision").with(user("bob")).with(csrf()).contentType("application/json").content(body(a,"APPROVE")))
                .andExpect(status().isNotFound());
        mvc.perform(post(path+"/decision").with(user("alice")).contentType("application/json").content(body(a,"APPROVE")))
                .andExpect(status().isForbidden());
        var injected=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(body(a,"APPROVE")); injected.put("ownerId",UUID.randomUUID().toString());
        mvc.perform(post(path+"/decision").with(user("alice")).with(csrf()).contentType("application/json").content(injected.toString()))
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE users SET role='VIEWER' WHERE username='alice'"); decide(a,"APPROVE",403);
        assertThat(trace(id(r)).path("status").asText()).isEqualTo("WAITING_FOR_APPROVAL");
    }
    @Test void digestMustMatchReviewedPayload() throws Exception {
        var r=pause(); var a=r.path("approvals").get(0).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)a).put("payloadDigest","0".repeat(64));
        decide(a,"APPROVE",409); assertThat(trace(id(r)).path("status").asText()).isEqualTo("WAITING_FOR_APPROVAL");
    }
    @Test void cancellationAbandonsApprovalAndRejectsLaterDecision() throws Exception {
        var r=pause(); var run=id(r); var a=r.path("approvals").get(0);
        mvc.perform(post("/api/runs/"+run+"/cancel").with(user("alice")).with(csrf())).andExpect(status().isOk());
        executions.advance(run); decide(a,"APPROVE",409);
        assertThat(trace(run).path("approvals").get(0).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(trace(run).path("context").has("result")).isFalse();
    }
    @Test void expiryAtBoundaryIsPersistedEvenWhenDecisionReturnsConflict() throws Exception {
        var r=pause(); var a=r.path("approvals").get(0);
        when(clock.instant()).thenReturn(NOW.plusSeconds(3600)); decide(a,"APPROVE",409);
        assertThat(trace(id(r)).path("errorCode").asText()).isEqualTo("APPROVAL_EXPIRED");
        assertThat(trace(id(r)).path("approvals").get(0).path("status").asText()).isEqualTo("EXPIRED");
    }
    @Test void workerExpiresUnansweredRequestWithoutExecutingAction() throws Exception {
        var r=pause(); when(clock.instant()).thenReturn(NOW.plusSeconds(3601)); executions.advance(id(r));
        assertThat(trace(id(r)).path("errorCode").asText()).isEqualTo("APPROVAL_EXPIRED");
        assertThat(trace(id(r)).path("steps").get(4).path("attempts").asInt()).isZero();
    }
    @Test void roleRevocationAfterApprovalPreventsExecution() throws Exception {
        var r=pause(); decide(r.path("approvals").get(0),"APPROVE",200);
        jdbc.update("UPDATE users SET role='VIEWER' WHERE username='alice'"); executions.advance(id(r));
        assertThat(trace(id(r)).path("errorCode").asText()).isEqualTo("TOOL_ROLE_DENIED");
        assertThat(trace(id(r)).path("context").has("result")).isFalse();
    }
    @Test void changedCheckpointAfterApprovalCannotChangeAuthorizedAction() throws Exception {
        var r=pause(); decide(r.path("approvals").get(0),"APPROVE",200);
        var changed=(com.fasterxml.jackson.databind.node.ObjectNode)r.path("context").deepCopy(); changed.put("injected",true);
        jdbc.update("UPDATE workflow_runs SET context=? WHERE id=?",changed.toString(),id(r)); executions.advance(id(r));
        assertThat(trace(id(r)).path("errorCode").asText()).isEqualTo("APPROVAL_PAYLOAD_CHANGED");
        assertThat(trace(id(r)).path("context").has("result")).isFalse();
    }
    @Test void rollbackKeepsPauseAndDecisionAtomicWithEvents() throws Exception {
        UUID run=start(); for(int i=0;i<3;i++) executions.advance(run);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> { executions.advance(run); tx.setRollbackOnly(); });
        assertThat(trace(run).path("status").asText()).isEqualTo("RUNNING");
        assertThat(trace(run).path("approvals").size()).isZero(); executions.advance(run);
        var a=trace(run).path("approvals").get(0); int count=trace(run).path("events").size();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try { decide(a,"APPROVE",200); } catch(Exception e) { throw new RuntimeException(e); }
            tx.setRollbackOnly();
        });
        assertThat(trace(run).path("status").asText()).isEqualTo("WAITING_FOR_APPROVAL");
        assertThat(trace(run).path("events").size()).isEqualTo(count);
    }
    @Test void competingDecisionsHaveOneWinnerAndOneConflict() throws Exception {
        var r=pause(); var a=r.path("approvals").get(0); var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> approve=() -> { gate.await(5,TimeUnit.SECONDS); return decisionStatus(a,"APPROVE"); };
            Callable<Integer> reject=() -> { gate.await(5,TimeUnit.SECONDS); return decisionStatus(a,"REJECT"); };
            var x=pool.submit(approve); var y=pool.submit(reject); gate.countDown();
            assertThat(List.of(x.get(10,TimeUnit.SECONDS),y.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        long decisions=java.util.stream.StreamSupport.stream(trace(id(r)).path("events").spliterator(),false)
                .filter(e -> Set.of("APPROVAL_APPROVED","APPROVAL_REJECTED").contains(e.path("eventType").asText())).count();
        assertThat(decisions).isEqualTo(1);
    }
    @Test void inboxIsOwnerScopedAndBounded() throws Exception {
        var r=pause(); String aid=r.path("approvals").get(0).path("id").asText();
        var inbox=json.readTree(mvc.perform(get("/api/approvals").with(user("alice"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(inbox.path("items").toString()).contains(aid);
        var other=mvc.perform(get("/api/approvals").with(user("bob"))).andExpect(status().isOk()).andReturn();
        assertThat(other.getResponse().getContentAsString()).doesNotContain(aid);
        mvc.perform(get("/api/approvals?size=101").with(user("alice"))).andExpect(status().isBadRequest());
    }
    @Test void unansweredApprovalDoesNotStarveRunnableWork() throws Exception {
        var waiting=pause(); var next=start();
        assertThat(executions.runnable()).doesNotContain(id(waiting)).contains(next);
        when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
        assertThat(executions.runnable()).contains(id(waiting));
    }
    @Test void concurrentIdenticalDecisionsAreIdempotent() throws Exception {
        var r=pause(); var a=r.path("approvals").get(0); var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> call=() -> { gate.await(5,TimeUnit.SECONDS); return decisionStatus(a,"APPROVE"); };
            var x=pool.submit(call); var y=pool.submit(call); gate.countDown();
            assertThat(List.of(x.get(10,TimeUnit.SECONDS),y.get(10,TimeUnit.SECONDS))).containsExactly(200,200);
        }
        executions.advance(id(r));
        assertThat(trace(id(r)).path("steps").get(4).path("attempts").asInt()).isEqualTo(1);
    }
    @Test void cancellationCompetingWithApprovalPreventsFutureAction() throws Exception {
        var r=pause(); var a=r.path("approvals").get(0); var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var x=pool.submit(() -> { gate.await(5,TimeUnit.SECONDS); return decisionStatus(a,"APPROVE"); });
            var y=pool.submit(() -> { gate.await(5,TimeUnit.SECONDS); return mvc.perform(post("/api/runs/"+id(r)+"/cancel")
                    .with(user("alice")).with(csrf())).andReturn().getResponse().getStatus(); });
            gate.countDown(); assertThat(x.get(10,TimeUnit.SECONDS)).isIn(200,409); assertThat(y.get(10,TimeUnit.SECONDS)).isEqualTo(200);
        }
        executions.advance(id(r)); assertThat(trace(id(r)).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(trace(id(r)).path("context").has("result")).isFalse();
    }
    @Test void changedStoredPayloadCannotBeApprovedUsingItsOldDigest() throws Exception {
        var r=pause(); var a=r.path("approvals").get(0);
        jdbc.update("UPDATE approval_requests SET payload=? WHERE id=?","{}",UUID.fromString(a.path("id").asText()));
        decide(a,"APPROVE",409); assertThat(trace(id(r)).path("status").asText()).isEqualTo("WAITING_FOR_APPROVAL");
    }
    int decisionStatus(JsonNode a,String decision) throws Exception {
        return mvc.perform(post("/api/approvals/"+a.path("id").asText()+"/decision").with(user("alice")).with(csrf())
                .contentType("application/json").content(body(a,decision))).andReturn().getResponse().getStatus();
    }
    JsonNode decide(JsonNode a,String decision,int expected) throws Exception {
        var response=mvc.perform(post("/api/approvals/"+a.path("id").asText()+"/decision").with(user("alice")).with(csrf())
                .contentType("application/json").content(body(a,decision))).andExpect(status().is(expected)).andReturn();
        return json.readTree(response.getResponse().getContentAsString());
    }
    String body(JsonNode a,String decision) throws Exception { return json.writeValueAsString(Map.of("decision",decision,"payloadDigest",a.path("payloadDigest").asText())); }
    JsonNode trace(UUID run) throws Exception { return json.readTree(mvc.perform(get("/api/runs/"+run).with(user("alice"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    UUID id(JsonNode run) { return UUID.fromString(run.path("id").asText()); }
    JsonNode pause() throws Exception { var run=start(); for(int i=0;i<4;i++)executions.advance(run); return trace(run); }
    UUID start() throws Exception {
        String definition="""
        {"nodes":[{"id":"receive","type":"TRIGGER","label":"Receive"},
        {"id":"classify","type":"AI_STEP","label":"Classify","agent":{"goal":"CLASSIFY_ISSUE","allowedTools":["classify_issue"],"maxToolCalls":1}},
        {"id":"search","type":"AI_STEP","label":"Search","agent":{"goal":"FIND_DOCUMENTATION","allowedTools":["search_documentation"],"maxToolCalls":1}},
        {"id":"review","type":"APPROVAL","label":"Review","approval":{"timeoutSeconds":3600}},
        {"id":"record","type":"ACTION","label":"Record","operation":"RECORD_RESULT"}],
        "edges":[{"source":"receive","target":"classify"},{"source":"classify","target":"search"},{"source":"search","target":"review"},{"source":"review","target":"record"}]}
        """;
        String wf=json.readTree(mvc.perform(post("/api/workflows").with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("name","Approval test","description","Local action","definition",json.readTree(definition)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText();
        return id(json.readTree(mvc.perform(post("/api/workflows/"+wf+"/runs").with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("versionNumber",1,"idempotencyKey",UUID.randomUUID().toString(),"input",Map.of("subject","Cannot log in","body","Password reset failed")))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()));
    }
}
