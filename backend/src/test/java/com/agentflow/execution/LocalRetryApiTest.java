package com.agentflow.execution;

import com.agentflow.auth.UserRepository;
import com.agentflow.tools.ToolRegistry;
import com.agentflow.workflow.WorkflowDefinition.Operation;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
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
class LocalRetryApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ExecutionService executions;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean Clock clock;
    @MockitoSpyBean LocalActions actions;
    static final Instant NOW=Instant.parse("2026-10-10T00:00:00Z");
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        jdbc.update("UPDATE users SET role='USER' WHERE username='alice'");
    }
    @AfterEach void restoreRole() { jdbc.update("UPDATE users SET role='USER' WHERE username='alice'"); }

    @Test void persistsBackoffAndOnlyRetriesAtDueTimeWithoutChangingCheckpoint() throws Exception {
        var run=start(ExecutionApiTest.DEFINITION); executions.advance(run);
        fail(Operation.CLASSIFY_ISSUE,2); executions.advance(run);
        var first=trace(run); var step=first.path("steps").get(1);
        assertThat(first.path("status").asText()).isEqualTo("RUNNING");
        assertThat(step.path("status").asText()).isEqualTo("PENDING");
        assertThat(step.path("attempts").asInt()).isEqualTo(1);
        assertThat(step.path("errorCode").asText()).isEqualTo("LOCAL_TRANSIENT_FAILURE");
        assertThat(first.path("context").has("classification")).isFalse();
        var due=Instant.parse(step.path("nextAttemptAt").asText());
        assertThat(due).isBetween(NOW.plusSeconds(2),NOW.plusMillis(2500));
        when(clock.instant()).thenReturn(due.minusNanos(1)); executions.advance(run);
        assertThat(trace(run)).isEqualTo(first);
        when(clock.instant()).thenReturn(due); executions.advance(run);
        var second=trace(run).path("steps").get(1);
        assertThat(second.path("attempts").asInt()).isEqualTo(2);
        var finalDue=Instant.parse(second.path("nextAttemptAt").asText());
        assertThat(finalDue).isBetween(due.plusSeconds(4),due.plusMillis(4500));
        when(clock.instant()).thenReturn(finalDue); executions.advance(run);
        var succeeded=trace(run).path("steps").get(1);
        assertThat(succeeded.path("attempts").asInt()).isEqualTo(3);
        assertThat(succeeded.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.path("errorCode").isNull()).isTrue();
        assertThat(succeeded.path("nextAttemptAt").isNull()).isTrue();
        executions.advance(run); executions.advance(run);
        assertThat(trace(run).path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(retryEvents(trace(run))).isEqualTo(2);
    }

    @Test void exhaustsAfterThreeAttemptsAndCancelsLaterActions() throws Exception {
        var run=start(ExecutionApiTest.DEFINITION); executions.advance(run);
        fail(Operation.CLASSIFY_ISSUE,100);
        for(int i=0;i<3;i++) { executions.advance(run); if(i<2) due(run,1); }
        executions.advance(run);
        var r=trace(run);
        assertThat(r.path("status").asText()).isEqualTo("FAILED");
        assertThat(r.path("errorCode").asText()).isEqualTo("LOCAL_TRANSIENT_FAILURE");
        assertThat(r.path("steps").get(1).path("attempts").asInt()).isEqualTo(3);
        assertThat(r.path("steps").get(1).path("nextAttemptAt").isNull()).isTrue();
        assertThat(r.path("steps").get(2).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(r.path("context").has("result")).isFalse();
        assertThat(retryEvents(r)).isEqualTo(2);
    }

    @Test void denialsValidationAndUnclassifiedErrorsAreNeverRetriedOrLeaked() throws Exception {
        for(var failure:List.of(new ToolRegistry.Denied("TOOL_ROLE_DENIED"),new LocalActions.StepFailure("MISSING_ISSUE"),
                new IllegalStateException("secret-provider-body"))) {
            reset(actions); var run=start(ExecutionApiTest.DEFINITION); executions.advance(run);
            doThrow(failure).when(actions).execute(eq(Operation.CLASSIFY_ISSUE),any(),any()); executions.advance(run);
            var r=trace(run);
            assertThat(r.path("status").asText()).isEqualTo("FAILED");
            assertThat(r.path("steps").get(1).path("attempts").asInt()).isEqualTo(1);
            assertThat(retryEvents(r)).isZero(); assertThat(r.toString()).doesNotContain("secret-provider-body");
        }
    }

    @Test void cancellationClearsScheduledRetryAndPreventsLaterAction() throws Exception {
        var run=start(ExecutionApiTest.DEFINITION); executions.advance(run);
        fail(Operation.CLASSIFY_ISSUE,100); executions.advance(run);
        mvc.perform(post("/api/runs/"+run+"/cancel").with(user("alice")).with(csrf())).andExpect(status().isOk());
        when(clock.instant()).thenReturn(NOW.plusSeconds(10)); executions.advance(run);
        var r=trace(run);
        assertThat(r.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(r.path("steps").get(1).path("attempts").asInt()).isEqualTo(1);
        assertThat(r.path("steps").get(1).path("nextAttemptAt").isNull()).isTrue();
        assertThat(r.path("context").has("classification")).isFalse();
    }

    @Test void deterministicRunsAlsoEnforceElapsedDeadlineBeforeRetry() throws Exception {
        var run=start(ExecutionApiTest.DEFINITION); executions.advance(run);
        fail(Operation.CLASSIFY_ISSUE,100); executions.advance(run);
        when(clock.instant()).thenReturn(NOW.plusSeconds(120)); executions.advance(run);
        var r=trace(run);
        assertThat(r.path("status").asText()).isEqualTo("FAILED");
        assertThat(r.path("errorCode").asText()).isEqualTo("RUN_DEADLINE_EXCEEDED");
        assertThat(retryEvents(r)).isEqualTo(1);
    }

    @Test void retryScheduleCheckpointAndAuditRollbackTogether() throws Exception {
        var run=start(ExecutionApiTest.DEFINITION); executions.advance(run); fail(Operation.CLASSIFY_ISSUE,100);
        var before=trace(run);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {executions.advance(run);tx.setRollbackOnly();});
        assertThat(trace(run)).isEqualTo(before);
        executions.advance(run);
        assertThat(trace(run).path("steps").get(1).path("attempts").asInt()).isEqualTo(1);
        assertThat(retryEvents(trace(run))).isEqualTo(1);
    }

    @Test void competingWorkersScheduleOnlyOneRetryAtEachDueTime() throws Exception {
        var run=start(ExecutionApiTest.DEFINITION); executions.advance(run); fail(Operation.CLASSIFY_ISSUE,100);
        concurrentAdvance(run);
        assertThat(trace(run).path("steps").get(1).path("attempts").asInt()).isEqualTo(1);
        due(run,1); concurrentAdvance(run);
        assertThat(trace(run).path("steps").get(1).path("attempts").asInt()).isEqualTo(2);
        assertThat(retryEvents(trace(run))).isEqualTo(2);
    }

    @Test void agentToolFailureIsTerminalWithoutRepeatingPlanner() throws Exception {
        var run=start(APPROVAL_DEFINITION); executions.advance(run);
        fail(Operation.CLASSIFY_ISSUE,100); executions.advance(run);
        var r=trace(run);
        assertThat(r.path("status").asText()).isEqualTo("FAILED");
        assertThat(r.path("plannerCalls").asInt()).isEqualTo(1);
        assertThat(r.path("steps").get(1).path("attempts").asInt()).isEqualTo(1);
        assertThat(retryEvents(r)).isZero();
    }

    @Test void approvedActionRechecksRoleBeforeEveryRetry() throws Exception {
        var run=start(APPROVAL_DEFINITION); for(int i=0;i<4;i++) executions.advance(run);
        var a=trace(run).path("approvals").get(0);
        mvc.perform(post("/api/approvals/"+a.path("id").asText()+"/decision").with(user("alice")).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("decision","APPROVE","payloadDigest",a.path("payloadDigest").asText()))))
                .andExpect(status().isOk());
        fail(Operation.RECORD_RESULT,100); executions.advance(run); due(run,4);
        jdbc.update("UPDATE users SET role='VIEWER' WHERE username='alice'"); executions.advance(run);
        var r=trace(run);
        assertThat(r.path("status").asText()).isEqualTo("FAILED");
        assertThat(r.path("errorCode").asText()).isEqualTo("TOOL_ROLE_DENIED");
        assertThat(r.path("context").has("result")).isFalse();
        assertThat(retryEvents(r)).isEqualTo(1);
    }

    private void fail(Operation operation,int times) {
        var remaining=new java.util.concurrent.atomic.AtomicInteger(times);
        doAnswer(call -> {
            if(remaining.getAndDecrement()>0) throw new LocalActions.TransientFailure();
            return call.callRealMethod();
        }).when(actions).execute(eq(operation),any(),any());
    }
    private void due(UUID run,int ordinal) throws Exception {
        when(clock.instant()).thenReturn(Instant.parse(trace(run).path("steps").get(ordinal).path("nextAttemptAt").asText()));
    }
    private long retryEvents(JsonNode r) {
        return java.util.stream.StreamSupport.stream(r.path("events").spliterator(),false)
                .filter(e -> e.path("eventType").asText().equals("STEP_RETRY_SCHEDULED")).count();
    }
    private void concurrentAdvance(UUID run) throws Exception {
        var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Void> call=() -> {assertThat(gate.await(5,TimeUnit.SECONDS)).isTrue();executions.advance(run);return null;};
            var a=executor.submit(call);var b=executor.submit(call);gate.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
    }
    private UUID start(String definition) throws Exception {
        var body=json.writeValueAsString(Map.of("name","Retry contract","description","Pure local handlers","definition",json.readTree(definition)));
        var created=mvc.perform(post("/api/workflows").with(user("alice")).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        String workflow=json.readTree(created.getResponse().getContentAsString()).path("id").asText();
        var request=json.writeValueAsString(Map.of("versionNumber",1,"idempotencyKey",UUID.randomUUID().toString(),"input",Map.of("subject","Cannot log in","body","Password reset failed")));
        var started=mvc.perform(post("/api/workflows/"+workflow+"/runs").with(user("alice")).with(csrf()).contentType("application/json").content(request))
                .andExpect(status().isAccepted()).andReturn();
        return UUID.fromString(json.readTree(started.getResponse().getContentAsString()).path("id").asText());
    }
    private JsonNode trace(UUID run) throws Exception {
        return json.readTree(mvc.perform(get("/api/runs/"+run).with(user("alice"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    static final String APPROVAL_DEFINITION="""
        {"nodes":[{"id":"receive","type":"TRIGGER","label":"Receive"},
        {"id":"classify","type":"AI_STEP","label":"Classify","agent":{"goal":"CLASSIFY_ISSUE","allowedTools":["classify_issue"],"maxToolCalls":1}},
        {"id":"search","type":"AI_STEP","label":"Search","agent":{"goal":"FIND_DOCUMENTATION","allowedTools":["search_documentation"],"maxToolCalls":1}},
        {"id":"review","type":"APPROVAL","label":"Review","approval":{"timeoutSeconds":3600}},
        {"id":"record","type":"ACTION","label":"Record","operation":"RECORD_RESULT"}],
        "edges":[{"source":"receive","target":"classify"},{"source":"classify","target":"search"},{"source":"search","target":"review"},{"source":"review","target":"record"}]}
        """;
}
