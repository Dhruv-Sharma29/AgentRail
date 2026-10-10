package com.agentflow.agent;

import com.agentflow.tools.ToolRegistry;
import com.agentflow.workflow.WorkflowDefinition.AgentGoal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiPlannerTest {
    final ObjectMapper json=new ObjectMapper();
    final jakarta.validation.ValidatorFactory validation=Validation.buildDefaultValidatorFactory();
    final ToolRegistry registry=new ToolRegistry(json,validation.getValidator());
    final RestClient.Builder rest=RestClient.builder();
    final MockRestServiceServer server=MockRestServiceServer.bindTo(rest).build();
    final OpenAiPlanner planner=new OpenAiPlanner(OpenAiPlanner.model("test-key",rest),registry,json);
    @AfterEach void close() { validation.close(); }

    @Test void sendsOnlyAllowedToolSchemasAndReturnsUnexecutedArgumentsWithUsage() throws Exception {
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
            .andExpect(jsonPath("$.model").value("gpt-4.1-mini-2025-04-14"))
            .andExpect(jsonPath("$.max_completion_tokens").value(1024))
            .andExpect(jsonPath("$.tool_choice").value("required"))
            .andExpect(jsonPath("$.parallel_tool_calls").value(false))
            .andExpect(jsonPath("$.store").value(false))
            .andExpect(jsonPath("$.tools.length()").value(1))
            .andExpect(jsonPath("$.tools[0].function.name").value("classify_issue"))
            .andRespond(withSuccess(response("tool_calls", "{\\\"subject\\\":\\\"Cannot log in\\\",\\\"body\\\":\\\"Password reset failed\\\"}"),MediaType.APPLICATION_JSON));
        var result=planner.propose(job());
        assertThat(result.totalTokens()).isEqualTo(123);
        assertThat(json.readTree(result.proposal()).path("toolCalls").get(0).path("tool").asText()).isEqualTo("classify_issue");
        server.verify();
    }
    @Test void preservesDuplicateArgumentKeysForJavaPolicyToReject() {
        server.expect(anything()).andRespond(withSuccess(response("tool_calls","{\\\"subject\\\":\\\"a\\\",\\\"subject\\\":\\\"b\\\"}"),MediaType.APPLICATION_JSON));
        assertThat(planner.propose(job()).proposal()).contains("\"subject\":\"a\",\"subject\":\"b\"");
    }
    @Test void incompleteResponseIsRejected() {
        server.expect(anything()).andRespond(withSuccess(response("length","{}"),MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> planner.propose(job())).isInstanceOf(LivePlanner.Failure.class);
    }
    @Test void authenticationFailureIsSafeAndNotRetried() {
        server.expect(anything()).andRespond(withUnauthorizedRequest().body("secret-remote-body"));
        assertThatThrownBy(() -> planner.propose(job())).hasMessage("PLANNER_AUTHENTICATION"); server.verify();
    }
    @Test void oversizedInputIsRejectedBeforeNetwork() {
        var job=job();
        var huge=new LivePlanner.Job(job.intentId(),job.runId(),job.nodeId(),"openai",job.model(),new Planner.Request(AgentGoal.CLASSIFY_ISSUE,"subject","a".repeat(17000),null,Set.of("classify_issue")),job.deadline());
        assertThatThrownBy(() -> planner.propose(huge)).isInstanceOf(LivePlanner.Failure.class); server.verify();
    }
    @Test void oversizedHttpResponseIsBoundedBeforeDeserialization() {
        var bounded=OpenAiPlanner.boundedHttp();
        var mock=MockRestServiceServer.bindTo(bounded).build();
        var limited=new OpenAiPlanner(OpenAiPlanner.model("test-key",bounded),registry,json);
        mock.expect(anything()).andRespond(withSuccess("x".repeat(65537),MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> limited.propose(job())).isInstanceOf(LivePlanner.Failure.class); mock.verify();
    }
    @Test void expiredIntentDoesNotStartNetworkWork() {
        var current=job(); var expired=new LivePlanner.Job(current.intentId(),current.runId(),current.nodeId(),"openai",current.model(),current.request(),Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> planner.propose(expired)).hasMessage("PLANNER_TIMEOUT"); server.verify();
    }
    @Test void rateLimitIsRedactedWithoutRetry() {
        server.expect(anything()).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).body("{\"error\":{\"code\":\"rate_limit_exceeded\",\"message\":\"sensitive-details\"}}"));
        assertThatThrownBy(() -> planner.propose(job())).hasMessage("PLANNER_RATE_LIMIT"); server.verify();
    }
    @Test void exhaustedQuotaIsDistinctFromTransientRateLimiting() {
        server.expect(anything()).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).body("{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"sensitive-billing-details\"}}"));
        assertThatThrownBy(() -> planner.propose(job())).hasMessage("PLANNER_QUOTA"); server.verify();
    }
    @Test void unstructured429DoesNotGuessTheFailureClass() {
        server.expect(anything()).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).body("sensitive-unknown-details"));
        assertThatThrownBy(() -> planner.propose(job())).hasMessage("PLANNER_ACCESS_LIMIT"); server.verify();
    }
    private LivePlanner.Job job() {
        return new LivePlanner.Job(UUID.randomUUID(),UUID.randomUUID(),"classify","openai","gpt-4.1-mini-2025-04-14",new Planner.Request(AgentGoal.CLASSIFY_ISSUE,"Cannot log in","Password reset failed",null,Set.of("classify_issue")),Instant.now().plusSeconds(35));
    }
    private String response(String finish,String arguments) {
        return "{\"id\":\"synthetic\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"gpt-4.1-mini-2025-04-14\",\"choices\":[{\"index\":0,\"finish_reason\":\""+finish+"\",\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call1\",\"type\":\"function\",\"function\":{\"name\":\"classify_issue\",\"arguments\":\""+arguments+"\"}}]}}],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":23,\"total_tokens\":123}}";
    }
}
