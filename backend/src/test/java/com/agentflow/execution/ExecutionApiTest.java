package com.agentflow.execution;

import com.agentflow.auth.AppUser;
import com.agentflow.auth.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExecutionApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ExecutionService executions;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void ensureBobExists() {
        if (users.findByUsername("bob").isEmpty()) users.save(new AppUser("bob", "unused-test-hash"));
    }

    @Test void startsAnImmutableVersionWithPendingSteps() throws Exception {
        String workflow = workflow();
        var run = start(workflow, request("first"));
        assertThat(run.get("status").asText()).isEqualTo("PENDING");
        assertThat(run.get("versionNumber").asInt()).isEqualTo(1);
        assertThat(run.get("steps").size()).isEqualTo(4);
        mvc.perform(get("/api/workflows/" + workflow + "/runs").with(user("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
    }

    @Test void idempotencyReplaysSameRunAndConflictsOnChangedInput() throws Exception {
        String workflow = workflow();
        var run = start(workflow, request("same"));
        assertThat(start(workflow, request("same")).get("id")).isEqualTo(run.get("id"));
        var changed = (ObjectNode) json.readTree(request("same"));
        ((ObjectNode) changed.get("input")).put("subject", "Changed issue");
        mvc.perform(post("/api/workflows/" + workflow + "/runs").with(user("alice")).with(csrf())
                .contentType("application/json").content(changed.toString())).andExpect(status().isConflict());
    }

    @Test void protectsRunReadsStartsHistoryAndCancellationByOwner() throws Exception {
        String workflow = workflow();
        String run = start(workflow, request("private")).get("id").asText();
        mvc.perform(get("/api/runs/" + run).with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/workflows/" + workflow + "/runs").with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/runs/" + run + "/cancel").with(user("bob")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/workflows/" + workflow + "/runs").with(user("bob")).with(csrf())
                .contentType("application/json").content(request("stolen"))).andExpect(status().isNotFound());
    }

    @Test void cancellationIsDurableAndIdempotent() throws Exception {
        String run = start(workflow(), request("cancel")).get("id").asText();
        mvc.perform(post("/api/runs/" + run + "/cancel").with(user("alice")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/runs/" + run + "/cancel").with(user("alice")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/runs/" + run).with(user("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.finishedAt").isNotEmpty())
                .andExpect(jsonPath("$.steps[0].status").value("CANCELLED"));
    }

    @Test void rejectsMissingVersionBadInputUnknownFieldsAndArchivedWorkflow() throws Exception {
        String workflow = workflow();
        var request = (ObjectNode) json.readTree(request("invalid"));
        request.put("versionNumber", 999);
        mvc.perform(post("/api/workflows/" + workflow + "/runs").with(user("alice")).with(csrf())
                .contentType("application/json").content(request.toString())).andExpect(status().isNotFound());
        request.put("versionNumber", 1);
        ((ObjectNode) request.get("input")).put("subject", " ");
        mvc.perform(post("/api/workflows/" + workflow + "/runs").with(user("alice")).with(csrf())
                .contentType("application/json").content(request.toString())).andExpect(status().isBadRequest());
        request = (ObjectNode) json.readTree(request("injected"));
        request.put("ownerId", UUID.randomUUID().toString());
        mvc.perform(post("/api/workflows/" + workflow + "/runs").with(user("alice")).with(csrf())
                .contentType("application/json").content(request.toString())).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/workflows/" + workflow).with(user("alice")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post("/api/workflows/" + workflow + "/runs").with(user("alice")).with(csrf())
                .contentType("application/json").content(request("archived"))).andExpect(status().isConflict());
    }

    @Test void requiresAuthenticationCsrfAndBoundedPagination() throws Exception {
        String workflow = workflow();
        mvc.perform(get("/api/runs/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/workflows/" + workflow + "/runs").with(user("alice"))
                .contentType("application/json").content(request("csrf"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/workflows/" + workflow + "/runs?size=101").with(user("alice"))).andExpect(status().isBadRequest());
    }

    @Test void advancesOneStepPerTransactionAndResumesFromPersistedCheckpoint() throws Exception {
        String run = start(workflow(), request("checkpoint")).get("id").asText();
        executions.advance(UUID.fromString(run));
        mvc.perform(get("/api/runs/" + run).with(user("alice")))
                .andExpect(jsonPath("$.status").value("RUNNING")).andExpect(jsonPath("$.steps[0].attempts").value(1))
                .andExpect(jsonPath("$.steps[0].input.subject").value("Cannot log in"))
                .andExpect(jsonPath("$.steps[1].status").value("PENDING"));
        assertThat(executions.runnable()).contains(UUID.fromString(run));
        for (int i = 0; i < 4; i++) executions.advance(UUID.fromString(run));
        mvc.perform(get("/api/runs/" + run).with(user("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED")).andExpect(jsonPath("$.context.result.externalTicketCreated").value(false))
                .andExpect(jsonPath("$.steps[0].attempts").value(1)).andExpect(jsonPath("$.steps[3].attempts").value(1))
                .andExpect(jsonPath("$.events.length()").value(7));
        mvc.perform(post("/api/runs/" + run + "/cancel").with(user("alice")).with(csrf())).andExpect(status().isConflict());
    }

    @Test void cancellationPreventsLaterStepsAndKeepsCompletedCheckpoint() throws Exception {
        String run = start(workflow(), request("stop")).get("id").asText();
        executions.advance(UUID.fromString(run));
        mvc.perform(post("/api/runs/" + run + "/cancel").with(user("alice")).with(csrf())).andExpect(status().isOk());
        executions.advance(UUID.fromString(run));
        mvc.perform(get("/api/runs/" + run).with(user("alice")))
                .andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.steps[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[1].status").value("CANCELLED")).andExpect(jsonPath("$.steps[1].attempts").value(0));
    }

    @Test void failedStepRecordsSafeErrorAndPreventsLaterActions() throws Exception {
        String definition = DEFINITION.replace("\"operation\":\"CLASSIFY_ISSUE\"", "\"operation\":\"RECORD_RESULT\"");
        String run = start(workflow(definition), request("fail")).get("id").asText();
        executions.advance(UUID.fromString(run));
        executions.advance(UUID.fromString(run));
        executions.advance(UUID.fromString(run));
        mvc.perform(get("/api/runs/" + run).with(user("alice")))
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.errorCode").value("MISSING_SUPPORT_CONTEXT"))
                .andExpect(jsonPath("$.steps[1].status").value("FAILED")).andExpect(jsonPath("$.steps[1].attempts").value(1))
                .andExpect(jsonPath("$.steps[2].status").value("CANCELLED")).andExpect(jsonPath("$.steps[2].attempts").value(0))
                .andExpect(jsonPath("$.events[3].eventType").value("STEP_FAILED"))
                .andExpect(jsonPath("$.events[4].eventType").value("RUN_FAILED"));
        assertThat(executions.runnable()).doesNotContain(UUID.fromString(run));
    }

    @Test void unsupportedNodesNeverCreateARunOrBypassApproval() throws Exception {
        String workflow = workflow(DEFINITION.replace("\"type\":\"ACTION\",\"label\":\"Classify issue\"", "\"type\":\"APPROVAL\",\"label\":\"Classify issue\""));
        mvc.perform(post("/api/workflows/" + workflow + "/runs").with(user("alice")).with(csrf())
                .contentType("application/json").content(request("unsupported"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/workflows/" + workflow + "/runs").with(user("alice")))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test void laterWorkflowEditsAndArchiveDoNotChangeAnExistingRun() throws Exception {
        String workflow = workflow();
        var original = start(workflow, request("version"));
        var update = Map.of("name", "Edited", "description", "Changed graph", "expectedVersion", 1,
                "definition", Map.of("nodes", List.of(Map.of("id", "new", "type", "TRIGGER", "label", "New")), "edges", List.of()));
        mvc.perform(put("/api/workflows/" + workflow).with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(update))).andExpect(status().isOk());
        var differentVersion = (ObjectNode) json.readTree(request("version"));
        differentVersion.put("versionNumber", 2);
        mvc.perform(post("/api/workflows/" + workflow + "/runs").with(user("alice")).with(csrf())
                .contentType("application/json").content(differentVersion.toString())).andExpect(status().isConflict());
        mvc.perform(delete("/api/workflows/" + workflow).with(user("alice")).with(csrf())).andExpect(status().isNoContent());
        assertThat(start(workflow, request("version")).get("id")).isEqualTo(original.get("id"));
        for (int i = 0; i < 4; i++) executions.advance(UUID.fromString(original.get("id").asText()));
        mvc.perform(get("/api/runs/" + original.get("id").asText()).with(user("alice")))
                .andExpect(jsonPath("$.versionNumber").value(1)).andExpect(jsonPath("$.steps.length()").value(4))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test void interruptedTransactionRollsBackStepCheckpointAndAuditTogether() throws Exception {
        var run = UUID.fromString(start(workflow(), request("rollback")).get("id").asText());
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            executions.advance(run);
            transaction.setRollbackOnly();
        });
        mvc.perform(get("/api/runs/" + run).with(user("alice")))
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.steps[0].attempts").value(0))
                .andExpect(jsonPath("$.context.issue").doesNotExist()).andExpect(jsonPath("$.events.length()").value(1));
        executions.advance(run);
        mvc.perform(get("/api/runs/" + run).with(user("alice")))
                .andExpect(jsonPath("$.steps[0].attempts").value(1)).andExpect(jsonPath("$.events.length()").value(3));
    }

    @Test void concurrentDuplicateStartsCreateOneRun() throws Exception {
        String workflow = workflow();
        String body = request("concurrent-start");
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> call = () -> {
                assertThat(gate.await(5, TimeUnit.SECONDS)).isTrue();
                return start(workflow, body).get("id").asText();
            };
            var a = executor.submit(call); var b = executor.submit(call); gate.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
        }
        mvc.perform(get("/api/workflows/" + workflow + "/runs").with(user("alice"))).andExpect(jsonPath("$.total").value(1));
    }

    @Test void concurrentWorkersAdvanceDistinctStepsWithoutDuplicatingAttempts() throws Exception {
        var run = UUID.fromString(start(workflow(), request("workers")).get("id").asText());
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Void> call = () -> {
                assertThat(gate.await(5, TimeUnit.SECONDS)).isTrue(); executions.advance(run); return null;
            };
            var a = executor.submit(call); var b = executor.submit(call); gate.countDown();
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        mvc.perform(get("/api/runs/" + run).with(user("alice")))
                .andExpect(jsonPath("$.steps[0].attempts").value(1)).andExpect(jsonPath("$.steps[1].attempts").value(1))
                .andExpect(jsonPath("$.steps[2].attempts").value(0)).andExpect(jsonPath("$.events.length()").value(4));
    }

    String workflow() throws Exception {
        return workflow(DEFINITION);
    }
    String workflow(String definition) throws Exception {
        String body = json.writeValueAsString(Map.of("name", "Deterministic intake", "description", "Local actions only", "definition", json.readTree(definition)));
        var result = mvc.perform(post("/api/workflows").with(user("alice")).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
    com.fasterxml.jackson.databind.JsonNode start(String workflow, String request) throws Exception {
        var result = mvc.perform(post("/api/workflows/" + workflow + "/runs").with(user("alice")).with(csrf())
                .contentType("application/json").content(request)).andExpect(status().isAccepted()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    String request(String key) throws Exception {
        return json.writeValueAsString(Map.of("versionNumber", 1, "idempotencyKey", key,
                "input", Map.of("subject", "Cannot log in", "body", "Password reset failed")));
    }
    static final String DEFINITION = """
        {"nodes":[
          {"id":"receive","type":"TRIGGER","label":"Receive issue"},
          {"id":"classify","type":"ACTION","label":"Classify issue","operation":"CLASSIFY_ISSUE"},
          {"id":"search","type":"ACTION","label":"Find documentation","operation":"SEARCH_DOCUMENTATION"},
          {"id":"record","type":"ACTION","label":"Record result","operation":"RECORD_RESULT"}],
         "edges":[{"source":"receive","target":"classify"},{"source":"classify","target":"search"},{"source":"search","target":"record"}]}
        """;
}
