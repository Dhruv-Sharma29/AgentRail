package com.agentflow.execution;

import com.agentflow.agent.Planner;
import com.agentflow.agent.DeterministicPlanner;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AgentExecutionApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ExecutionService executions;
    @MockitoBean Planner planner;
    @MockitoBean Clock clock;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void deterministicByDefault() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-10T00:00:00Z"));
        var deterministic = new DeterministicPlanner(json);
        when(planner.id()).thenReturn("controlled-deterministic-planner");
        when(planner.propose(any())).thenAnswer(call -> deterministic.propose(call.getArgument(0)));
    }

    @Test void completesAgentWorkflowWithPersistedProposalsDecisionsAndUsage() throws Exception {
        String run = start(definition(2, 1));
        advance(run, 4);
        mvc.perform(get("/api/runs/" + run).with(user("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.mode").value("DETERMINISTIC_PLANNER"))
                .andExpect(jsonPath("$.plannerCalls").value(2)).andExpect(jsonPath("$.toolCalls").value(2))
                .andExpect(jsonPath("$.steps[1].plannerId").value("controlled-deterministic-planner"))
                .andExpect(jsonPath("$.steps[1].policyStatus").value("APPROVED"))
                .andExpect(jsonPath("$.steps[1].proposal.toolCalls[0].tool").value("classify_issue"))
                .andExpect(jsonPath("$.context.result.classification.category").value("ACCOUNT"))
                .andExpect(jsonPath("$.context.result.externalTicketCreated").value(false));
    }

    @Test void maliciousSecondCallCannotExecuteTheFirstCallInABatch() throws Exception {
        doReturn("{\"toolCalls\":[" + CLASSIFY + ",{\"tool\":\"create_ticket\",\"arguments\":{\"approved\":true}}]}").when(planner).propose(any());
        String run = start(definition(2, 3));
        advance(run, 3);
        assertFailed(run, "UNKNOWN_TOOL", 1, 0);
        mvc.perform(get("/api/runs/" + run).with(user("alice")))
                .andExpect(jsonPath("$.context.classification").doesNotExist())
                .andExpect(jsonPath("$.steps[1].policyStatus").value("DENIED"))
                .andExpect(jsonPath("$.steps[1].proposal").isEmpty());
    }

    @Test void exhaustedNodeBudgetDeniesEntireProposal() throws Exception {
        doReturn("{\"toolCalls\":[" + CLASSIFY + "," + CLASSIFY + "]}").when(planner).propose(any());
        String run = start(definition(2, 1));
        advance(run, 2);
        assertFailed(run, "TOOL_BUDGET_EXHAUSTED", 1, 0);
    }

    @Test void runPlannerBudgetStopsRecursionAndPersistsAcrossCheckpoints() throws Exception {
        String run = start(definition(6, 1));
        advance(run, 8);
        assertFailed(run, "PLANNER_BUDGET_EXHAUSTED", 5, 5);
    }

    @Test void runToolBudgetCannotBeResetByMovingToAnotherNode() throws Exception {
        doReturn("{\"toolCalls\":[" + CLASSIFY + "," + CLASSIFY + "," + CLASSIFY + "]}").when(planner).propose(any());
        String run = start(definition(4, 3));
        advance(run, 6);
        assertFailed(run, "TOOL_BUDGET_EXHAUSTED", 4, 9);
    }

    @Test void plannerFailuresDoNotLeakDetailsOrExecuteTools() throws Exception {
        doThrow(new IllegalStateException("provider-secret-and-internal-detail")).when(planner).propose(any());
        String run = start(definition(2, 1));
        advance(run, 2);
        assertFailed(run, "PLANNER_FAILURE", 1, 0);
        var response = mvc.perform(get("/api/runs/" + run).with(user("alice"))).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("provider-secret-and-internal-detail");
    }

    @Test void malformedOutputAndSpoofedOwnershipFailClosed() throws Exception {
        doReturn("{\"toolCalls\":[" + CLASSIFY.replace("\"body\":", "\"ownerId\":\"bob\",\"body\":") + "]}").when(planner).propose(any());
        String run = start(definition(2, 1));
        advance(run, 2);
        assertFailed(run, "INVALID_TOOL_ARGUMENTS", 1, 0);
    }

    @Test void workflowToolPermissionsCannotBeExpandedByPlanner() throws Exception {
        var definition = definition(2, 1);
        ((ObjectNode) definition.get("nodes").get(1).get("agent")).putArray("allowedTools").add("search_documentation");
        String run = start(definition);
        advance(run, 2);
        assertFailed(run, "TOOL_NOT_ALLOWED", 1, 0);
    }

    @Test void cancellationAndOwnerChecksStillProtectAgentRuns() throws Exception {
        String run = start(definition(2, 1));
        advance(run, 1);
        mvc.perform(get("/api/runs/" + run).with(user("nobody"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/runs/" + run + "/cancel").with(user("alice"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/runs/" + run + "/cancel").with(user("alice")).with(csrf())).andExpect(status().isOk());
        advance(run, 2);
        mvc.perform(get("/api/runs/" + run).with(user("alice")))
                .andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.plannerCalls").value(0));
    }

    @Test void toolCatalogRequiresLoginAndCannotBeRegisteredThroughApi() throws Exception {
        mvc.perform(get("/api/tools")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tools").with(user("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].risk").value("READ_ONLY"))
                .andExpect(jsonPath("$[0].argumentSchema.additionalProperties").value(false));
        mvc.perform(post("/api/tools").with(user("alice")).with(csrf())).andExpect(status().isMethodNotAllowed());
    }

    @Test void elapsedBudgetPreventsFurtherPlanning() throws Exception {
        String run = start(definition(2, 1));
        advance(run, 1);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-10T00:02:01Z"));
        advance(run, 1);
        assertFailed(run, "RUN_DEADLINE_EXCEEDED", 0, 0);
    }

    @Test void elapsedBudgetAlsoPreventsTheFinalAction() throws Exception {
        String run = start(definition(2, 1));
        advance(run, 3);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-10T00:02:01Z"));
        advance(run, 1);
        assertFailed(run, "RUN_DEADLINE_EXCEEDED", 2, 2);
    }

    @Test void rollbackRestoresPlannerBudgetProposalAndToolCheckpointTogether() throws Exception {
        String run = start(definition(2, 1));
        advance(run, 1);
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            advance(run, 1); transaction.setRollbackOnly();
        });
        mvc.perform(get("/api/runs/" + run).with(user("alice")))
                .andExpect(jsonPath("$.plannerCalls").value(0)).andExpect(jsonPath("$.toolCalls").value(0))
                .andExpect(jsonPath("$.steps[1].proposal").isEmpty()).andExpect(jsonPath("$.steps[1].policyStatus").isEmpty())
                .andExpect(jsonPath("$.context.classification").doesNotExist()).andExpect(jsonPath("$.events.length()").value(3));
        advance(run, 3);
        mvc.perform(get("/api/runs/" + run).with(user("alice")))
                .andExpect(jsonPath("$.status").value("SUCCEEDED")).andExpect(jsonPath("$.plannerCalls").value(2))
                .andExpect(jsonPath("$.toolCalls").value(2)).andExpect(jsonPath("$.steps[1].attempts").value(1));
    }

    @Test void roleRevocationIsRecheckedFromDatabaseBeforeToolExecution() throws Exception {
        String run = start(definition(2, 1));
        advance(run, 1);
        jdbc.update("update users set role = ? where username = ?", "GUEST", "alice");
        try {
            advance(run, 1);
            assertFailed(run, "TOOL_ROLE_DENIED", 1, 0);
        } finally { jdbc.update("update users set role = ? where username = ?", "USER", "alice"); }
    }

    @Test void concurrentWorkersDoNotDuplicateProposalsOrSpendBudgetTwice() throws Exception {
        String run = start(definition(2, 1));
        advance(run, 1);
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Void> call = () -> {
                assertThat(gate.await(5, TimeUnit.SECONDS)).isTrue(); advance(run, 1); return null;
            };
            var first = executor.submit(call); var second = executor.submit(call); gate.countDown();
            first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
        }
        advance(run, 1);
        mvc.perform(get("/api/runs/" + run).with(user("alice")))
                .andExpect(jsonPath("$.status").value("SUCCEEDED")).andExpect(jsonPath("$.plannerCalls").value(2))
                .andExpect(jsonPath("$.toolCalls").value(2)).andExpect(jsonPath("$.steps[1].attempts").value(1))
                .andExpect(jsonPath("$.steps[2].attempts").value(1)).andExpect(jsonPath("$.events.length()").value(13));
    }

    private void assertFailed(String run, String code, int plannerCalls, int toolCalls) throws Exception {
        mvc.perform(get("/api/runs/" + run).with(user("alice")))
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.errorCode").value(code))
                .andExpect(jsonPath("$.plannerCalls").value(plannerCalls)).andExpect(jsonPath("$.toolCalls").value(toolCalls));
    }
    private void advance(String run, int times) { for (int i = 0; i < times; i++) executions.advance(UUID.fromString(run)); }
    private String start(ObjectNode definition) throws Exception {
        var created = mvc.perform(post("/api/workflows").with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Agent intake", "description", "Controlled planner", "definition", definition))))
                .andExpect(status().isCreated()).andReturn();
        String workflow = json.readTree(created.getResponse().getContentAsString()).get("id").asText();
        var result = mvc.perform(post("/api/workflows/" + workflow + "/runs").with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("versionNumber", 1, "idempotencyKey", "agent",
                        "input", Map.of("subject", "Cannot log in", "body", "Password reset failed")))))
                .andExpect(status().isAccepted()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
    private ObjectNode definition(int agents, int budget) {
        var definition = json.createObjectNode();
        var nodes = definition.putArray("nodes"); var edges = definition.putArray("edges");
        nodes.addObject().put("id", "receive").put("type", "TRIGGER").put("label", "Receive");
        String previous = "receive";
        for (int i = 1; i <= agents; i++) {
            String id = "agent" + i;
            var node = nodes.addObject().put("id", id).put("type", "AI_STEP").put("label", "Propose tool call");
            var settings = node.putObject("agent");
            settings.put("goal", agents == 2 && i == 2 ? "FIND_DOCUMENTATION" : "CLASSIFY_ISSUE");
            settings.put("maxToolCalls", budget).putArray("allowedTools").add("classify_issue").add("search_documentation");
            edges.addObject().put("source", previous).put("target", id); previous = id;
        }
        nodes.addObject().put("id", "record").put("type", "ACTION").put("label", "Record").put("operation", "RECORD_RESULT");
        edges.addObject().put("source", previous).put("target", "record");
        return definition;
    }
    private static final String CLASSIFY = "{\"tool\":\"classify_issue\",\"arguments\":{\"subject\":\"Cannot log in\",\"body\":\"Password reset failed\"}}";
}
