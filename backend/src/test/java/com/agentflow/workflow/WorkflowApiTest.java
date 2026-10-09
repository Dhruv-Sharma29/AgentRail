package com.agentflow.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentflow.auth.AppUser;
import com.agentflow.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

    @BeforeEach void ensureOtherUserExists() {
        if (users.findByUsername("bob").isEmpty()) users.save(new AppUser("bob", "unused-test-hash"));
    }

    @Test void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/workflows")).andExpect(status().isUnauthorized());
    }

    @Test void requiresCsrfOnMutations() throws Exception {
        mvc.perform(post("/api/workflows").with(user("alice")).contentType("application/json").content(body("Intake")))
                .andExpect(status().isForbidden());
    }

    @Test void exposesAnonymousCsrfTokenForLogin() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test void logsInUsingPersistedCredentials() throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf()).param("username", "alice")
                        .param("password", "test-password-not-production"))
                .andExpect(status().isNoContent()).andReturn();
        mvc.perform(get("/api/auth/me").session((org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
    }

    @Test void invalidPasswordReturns401() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "alice").param("password", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test void createsAndReadsAnOwnedWorkflow() throws Exception {
        String id = create("Support intake");
        mvc.perform(get("/api/workflows/" + id).with(user("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Support intake"))
                .andExpect(jsonPath("$.currentVersion").value(1)).andExpect(jsonPath("$.definition.nodes.length()").value(2));
        mvc.perform(get("/api/workflows").with(user("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test void deniesOtherUsersReadsUpdatesHistoryAndArchives() throws Exception {
        String id = create("Private workflow");
        mvc.perform(get("/api/workflows/" + id).with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/workflows/" + id + "/versions").with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/workflows/" + id).with(user("bob")).with(csrf()).contentType("application/json")
                .content(updateBody("Stolen", 1))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/workflows/" + id).with(user("bob")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/workflows").with(user("bob"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test void preservesHistoryAndRejectsStaleUpdates() throws Exception {
        String id = create("Original");
        mvc.perform(put("/api/workflows/" + id).with(user("alice")).with(csrf()).contentType("application/json")
                .content(updateBody("Updated", 1))).andExpect(status().isOk()).andExpect(jsonPath("$.currentVersion").value(2));
        mvc.perform(put("/api/workflows/" + id).with(user("alice")).with(csrf()).contentType("application/json")
                .content(updateBody("Stale", 1))).andExpect(status().isConflict());
        mvc.perform(get("/api/workflows/" + id + "/versions").with(user("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("Original"))
                .andExpect(jsonPath("$.items[1].name").value("Updated"));
    }

    @Test void archivesWithoutDeletingHistoryAndCannotUpdateArchivedWorkflow() throws Exception {
        String id = create("Archive me");
        mvc.perform(delete("/api/workflows/" + id).with(user("alice")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/workflows/" + id).with(user("alice")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/workflows/" + id).with(user("alice"))).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        mvc.perform(put("/api/workflows/" + id).with(user("alice")).with(csrf()).contentType("application/json")
                .content(updateBody("Revived", 1))).andExpect(status().isConflict());
        mvc.perform(get("/api/workflows/" + id + "/versions").with(user("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
    }

    @Test void rejectsUnknownFieldsAndInvalidGraphs() throws Exception {
        var injected = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(body("Injected"));
        injected.put("ownerId", "bob");
        mvc.perform(post("/api/workflows").with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(injected)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/workflows").with(user("alice")).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Invalid\",\"description\":\"\",\"definition\":{\"nodes\":[],\"edges\":[]}}"))
                .andExpect(status().isBadRequest());
    }

    @Test void rejectsInvalidPagination() throws Exception {
        mvc.perform(get("/api/workflows?size=1000").with(user("alice"))).andExpect(status().isBadRequest());
    }

    @Test void concurrentUpdatesCreateOnlyOneNewVersion() throws Exception {
        String id = create("Competing updates");
        String request = updateBody("Changed", 1);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> edit = () -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Race did not start");
                return mvc.perform(put("/api/workflows/" + id).with(user("alice")).with(csrf())
                        .contentType("application/json").content(request)).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(edit);
            var second = executor.submit(edit);
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        mvc.perform(get("/api/workflows/" + id + "/versions").with(user("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
    }

    @Test void invalidGraphUpdateDoesNotChangeVersionOrMetadata() throws Exception {
        String id = create("Unchanged");
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(updateBody("Corrupted", 1));
        ((com.fasterxml.jackson.databind.node.ObjectNode) request.get("definition")).putArray("edges");
        mvc.perform(put("/api/workflows/" + id).with(user("alice")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/workflows/" + id).with(user("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Unchanged")).andExpect(jsonPath("$.currentVersion").value(1));
    }

    String create(String name) throws Exception {
        var result = mvc.perform(post("/api/workflows").with(user("alice")).with(csrf()).contentType("application/json").content(body(name)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    String body(String name) throws Exception {
        return json.writeValueAsString(Map.of("name", name, "description", "Support operations", "definition", GraphValidatorTest.valid()));
    }

    String updateBody(String name, int expectedVersion) throws Exception {
        return json.writeValueAsString(Map.of("name", name, "description", "Support operations", "definition", GraphValidatorTest.valid(),
                "expectedVersion", expectedVersion));
    }
}
