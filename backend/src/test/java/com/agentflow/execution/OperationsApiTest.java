package com.agentflow.execution;

import com.agentflow.auth.AppUser;
import com.agentflow.auth.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationsApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    String username;
    UUID owner;

    @BeforeEach void createOwner() {
        username = "ops-" + UUID.randomUUID();
        owner = users.saveAndFlush(new AppUser(username, "unused-test-hash")).getId();
    }

    @Test void emptyWorkspaceReturnsZerosAndOnlyAggregateFields() throws Exception {
        var snapshot = snapshot();
        assertThat(snapshot.size()).isEqualTo(13);
        assertThat(Instant.parse(snapshot.get("capturedAt").asText())).isBeforeOrEqualTo(Instant.now());
        snapshot.fields().forEachRemaining(field -> {
            if (!field.getKey().equals("capturedAt")) assertThat(field.getValue().asLong()).isZero();
        });
    }

    @Test void reportsRunStatesRetryQueueAndOverdueApprovalsWithoutOtherOwners() throws Exception {
        var completed = run("SUCCEEDED");
        run("FAILED");
        var cancelled = run("CANCELLED");
        var running = run("RUNNING");
        var waiting = run("WAITING_FOR_APPROVAL");
        run("PENDING");
        scheduleRetry(running);
        scheduleRetry(cancelled); // A stale schedule on a terminal run is not runnable.
        approval(waiting, Instant.now().minusSeconds(10));
        var other = users.saveAndFlush(new AppUser("other-" + UUID.randomUUID(), "unused-test-hash"));
        jdbc.update("update workflow_runs set owner_id=? where id=?", other.getId(), completed);
        var foreignRetry = run("RUNNING");
        scheduleRetry(foreignRetry);
        jdbc.update("update workflow_runs set owner_id=? where id=?", other.getId(), foreignRetry);
        var foreignApproval = run("WAITING_FOR_APPROVAL");
        approval(foreignApproval, Instant.now().plusSeconds(60));
        jdbc.update("update workflow_runs set owner_id=? where id=?", other.getId(), foreignApproval);
        jdbc.update("update approval_requests set owner_id=? where run_id=?", other.getId(), foreignApproval);
        // Completed now belongs to another owner and must not affect this summary.
        var snapshot = snapshot();
        assertThat(snapshot.get("totalRuns").asLong()).isEqualTo(5);
        assertThat(snapshot.get("pendingRuns").asLong()).isEqualTo(1);
        assertThat(snapshot.get("runningRuns").asLong()).isEqualTo(1);
        assertThat(snapshot.get("waitingRuns").asLong()).isEqualTo(1);
        assertThat(snapshot.get("failedRuns").asLong()).isEqualTo(1);
        assertThat(snapshot.get("cancelledRuns").asLong()).isEqualTo(1);
        assertThat(snapshot.get("succeededRuns").asLong()).isZero();
        assertThat(snapshot.get("retryingSteps").asLong()).isEqualTo(1);
        assertThat(snapshot.get("pendingApprovals").asLong()).isEqualTo(1);
        assertThat(snapshot.get("overdueApprovals").asLong()).isEqualTo(1);
        mvc.perform(post("/api/runs/" + running + "/cancel").with(user(username)).with(csrf()))
                .andExpect(status().isOk());
        assertThat(snapshot().get("retryingSteps").asLong()).isZero();
    }

    @Test void countsUncertainAndSendingTicketsEvenOnCancelledRuns() throws Exception {
        ticket(run("CANCELLED"), "UNKNOWN");
        ticket(run("CANCELLED"), "SENDING");
        var foreign = run("FAILED");
        ticket(foreign, "UNKNOWN");
        jdbc.update("update workflow_runs set owner_id=? where id=?", users.findByUsername("alice").orElseThrow().getId(), foreign);
        var snapshot = snapshot();
        assertThat(snapshot.get("unknownTickets").asLong()).isEqualTo(1);
        assertThat(snapshot.get("sendingTickets").asLong()).isEqualTo(1);
    }

    @Test void authenticationAndCurrentRoleAreRequiredAndOwnerQueryCannotOverrideIdentity() throws Exception {
        mvc.perform(get("/api/operations")).andExpect(status().isUnauthorized());
        var foreign = run("FAILED");
        var other = users.saveAndFlush(new AppUser("foreign-" + UUID.randomUUID(), "unused-test-hash"));
        jdbc.update("update workflow_runs set owner_id=? where id=?", other.getId(), foreign);
        mvc.perform(get("/api/operations?ownerId=" + other.getId()).with(user(username)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRuns").value(0));
        jdbc.update("update users set role='REVOKED' where id=?", owner);
        mvc.perform(get("/api/operations").with(user(username))) // Existing session still claims USER.
                .andExpect(status().isForbidden());
    }

    JsonNode snapshot() throws Exception {
        return json.readTree(mvc.perform(get("/api/operations").with(user(username)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString());
    }

    UUID run(String state) throws Exception {
        var workflow = json.readTree(mvc.perform(post("/api/workflows").with(user(username)).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("name", "Operations fixture",
                        "description", "Private support input", "definition", json.readTree(ExecutionApiTest.DEFINITION)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var created = json.readTree(mvc.perform(post("/api/workflows/" + workflow.get("id").asText() + "/runs")
                .with(user(username)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("versionNumber", 1, "idempotencyKey", UUID.randomUUID().toString(),
                        "input", Map.of("subject", "Private issue", "body", "Must never appear in metrics")))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        var id = UUID.fromString(created.get("id").asText());
        jdbc.update("update workflow_runs set status=?, started_at=? where id=?", state, Timestamp.from(Instant.now()), id);
        return id;
    }

    UUID step(UUID run, int ordinal) {
        return jdbc.queryForObject("select id from node_executions where run_id=? and ordinal=?", UUID.class, run, ordinal);
    }
    void scheduleRetry(UUID run) {
        jdbc.update("update node_executions set next_attempt_at=?, attempts=1 where id=?",
                Timestamp.from(Instant.now().plusSeconds(5)), step(run, 0));
    }
    void approval(UUID run, Instant expiry) {
        jdbc.update("insert into approval_requests (id,run_id,owner_id,node_execution_id,target_node_execution_id,node_id,target_node_id,required_role,payload,payload_digest,status,created_at,expires_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), run, owner, step(run, 0), step(run, 1), "receive", "classify", "USER", "{}",
                "a".repeat(64), "PENDING", Timestamp.from(expiry.minusSeconds(60)), Timestamp.from(expiry));
    }
    void ticket(UUID run, String state) {
        jdbc.update("insert into ticket_executions (id,run_id,node_id,arguments,status,created_at,deadline) values (?,?,?,?,?,?,?)",
                step(run, 3), run, "record", "{}", state, Timestamp.from(Instant.now()), Timestamp.from(Instant.now().plusSeconds(20)));
    }
}
