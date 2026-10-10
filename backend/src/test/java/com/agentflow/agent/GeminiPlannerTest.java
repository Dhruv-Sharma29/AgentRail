package com.agentflow.agent;

import com.agentflow.tools.ToolRegistry;
import com.agentflow.workflow.WorkflowDefinition.AgentGoal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiPlannerTest {
    final ObjectMapper json = new ObjectMapper();
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final ToolRegistry registry = new ToolRegistry(json, validation.getValidator());
    final RestClient.Builder rest = RestClient.builder();
    final MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    final OpenAiPlanner planner = new OpenAiPlanner(OpenAiPlanner.model("test-gemini-key", rest, "gemini"), registry, json, "gemini");
    @AfterEach void close() { validation.close(); }

    @Test void sendsGeminiCredentialsToFixedGoogleEndpointAndReturnsUnexecutedProposal() throws Exception {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-gemini-key"))
            .andExpect(jsonPath("$.model").value("gemini-3.5-flash-lite"))
            .andExpect(jsonPath("$.max_tokens").value(1024))
            .andExpect(jsonPath("$.max_completion_tokens").doesNotExist())
            .andExpect(jsonPath("$.store").doesNotExist())
            .andExpect(jsonPath("$.parallel_tool_calls").doesNotExist())
            .andExpect(jsonPath("$.reasoning_effort").value("low"))
            .andExpect(jsonPath("$.tool_choice").value("required"))
            .andExpect(jsonPath("$.tools.length()").value(1))
            .andExpect(jsonPath("$.tools[0].function.name").value("classify_issue"))
            .andRespond(withSuccess(response("tool_calls", "classify_issue", "{\"subject\":\"Cannot log in\",\"body\":\"Password reset failed\"}"), MediaType.APPLICATION_JSON));
        var result = planner.propose(job());
        assertThat(result.totalTokens()).isEqualTo(123);
        assertThat(json.readTree(result.proposal()).path("toolCalls").get(0).path("tool").asText()).isEqualTo("classify_issue");
        server.verify();
    }
    @Test void providerMismatchIsRejectedBeforeNetwork() {
        var j = job();
        var mismatched = new LivePlanner.Job(j.intentId(), j.runId(), j.nodeId(), "openai", j.model(), j.request(), j.deadline());
        assertThatThrownBy(() -> planner.propose(mismatched)).hasMessage("PLANNER_UNAVAILABLE"); server.verify();
    }
    @Test void openAiModelCannotBeSentToGoogle() {
        var j = job();
        var mismatched = new LivePlanner.Job(j.intentId(), j.runId(), j.nodeId(), "gemini", "gpt-4.1-mini", j.request(), j.deadline());
        assertThatThrownBy(() -> planner.propose(mismatched)).hasMessage("PLANNER_FAILURE"); server.verify();
    }
    @Test void preservesDuplicateKeysAndJavaRejectsUnknownTools() {
        server.expect(anything()).andRespond(withSuccess(response("tool_calls", "create_ticket", "{\"subject\":\"a\",\"subject\":\"b\"}"), MediaType.APPLICATION_JSON));
        var raw = planner.propose(job()).proposal();
        assertThat(raw).contains("\"subject\":\"a\",\"subject\":\"b\"");
        var policy = new ProposalPolicy(json, registry);
        assertThatThrownBy(() -> policy.validate(raw, Set.of("classify_issue"), 1,
                new ToolRegistry.Context("USER", "Cannot log in", "Password reset failed", null)))
                .isInstanceOf(ToolRegistry.Denied.class);
    }
    @Test void googleAuthenticationErrorIsRedactedAndNotRetried() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":{\"code\":400,\"status\":\"INVALID_ARGUMENT\",\"message\":\"private-provider-details\",\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}"));
        assertThatThrownBy(() -> planner.propose(job())).hasMessage("PLANNER_AUTHENTICATION"); server.verify();
    }
    @Test void googleResourceExhaustedDoesNotGuessQuotaVersusRateLimit() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"private-limit-details\"}}"));
        assertThatThrownBy(() -> planner.propose(job())).hasMessage("PLANNER_ACCESS_LIMIT"); server.verify();
    }
    @Test void incompleteResponseIsRejected() {
        server.expect(anything()).andRespond(withSuccess(response("length", "classify_issue", "{}"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> planner.propose(job())).hasMessage("PLANNER_FAILURE"); server.verify();
    }
    private LivePlanner.Job job() {
        return new LivePlanner.Job(UUID.randomUUID(), UUID.randomUUID(), "classify", "gemini", "gemini-3.5-flash-lite",
                new Planner.Request(AgentGoal.CLASSIFY_ISSUE, "Cannot log in", "Password reset failed", null, Set.of("classify_issue")), Instant.now().plusSeconds(35));
    }
    private String response(String finish, String tool, String arguments) {
        try {
            return json.writeValueAsString(Map.of("id", "synthetic", "object", "chat.completion", "created", 1, "model", "gemini-3.5-flash-lite",
                    "choices", List.of(Map.of("index", 0, "finish_reason", finish, "message", Map.of("role", "assistant", "tool_calls",
                            List.of(Map.of("id", "call1", "type", "function", "function", Map.of("name", tool, "arguments", arguments)))))),
                    "usage", Map.of("prompt_tokens", 100, "completion_tokens", 23, "total_tokens", 123)));
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
