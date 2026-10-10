package com.agentflow.tools;

import com.agentflow.agent.ProposalPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class ProposalPolicyTest {
    private static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final ObjectMapper json = new ObjectMapper();
    private final ToolRegistry registry = new ToolRegistry(json, factory.getValidator());
    private final ProposalPolicy policy = new ProposalPolicy(json, registry);
    private final ToolRegistry.Context context = new ToolRegistry.Context("USER", "Cannot log in", "Password reset failed", "ACCOUNT");
    private static final String CLASSIFY = """
        {"tool":"classify_issue","arguments":{"subject":"Cannot log in","body":"Password reset failed"}}
        """;
    @AfterAll static void closeValidator() { factory.close(); }

    @Test void acceptsRegisteredAllowedCallAndFreezesItsArguments() {
        var decision = policy.validate("{\"toolCalls\":[" + CLASSIFY + "]}", Set.of("classify_issue"), 1, context);
        assertThat(decision.calls()).hasSize(1);
        assertThat(decision.calls().getFirst().tool()).isEqualTo("classify_issue");
        assertThat(decision.canonicalProposal()).contains("classify_issue");
        assertThat(registry.validate(decision.calls().getFirst().tool(), decision.calls().getFirst().arguments(), context).operation().name())
                .isEqualTo("CLASSIFY_ISSUE");
    }
    @ParameterizedTest @ValueSource(strings = {
        "{}", "null", "[]", "{\"toolCalls\":[]}", "{\"toolCalls\":null}",
        "{\"toolCalls\":[{\"tool\":\"classify_issue\",\"arguments\":[]}]}",
        "{\"toolCalls\":[{\"tool\":\"classify_issue\",\"arguments\":{},\"approved\":true}]}",
        "{\"toolCalls\":[],\"ownerId\":\"someone-else\"}",
        "{\"toolCalls\":[],\"toolCalls\":[]}", "{\"toolCalls\":[]} {}"
    })
    void rejectsMalformedOrAuthorityBearingProposals(String raw) {
        assertThatThrownBy(() -> policy.validate(raw, Set.of("classify_issue"), 1, context)).isInstanceOf(ToolRegistry.Denied.class);
    }
    @Test void deniesUnknownToolsAndWorkflowPermissionEscalation() {
        assertDenied("UNKNOWN_TOOL", "{\"toolCalls\":[{\"tool\":\"create_ticket\",\"arguments\":{}}]}", Set.of("create_ticket"), 1);
        assertDenied("TOOL_NOT_ALLOWED", "{\"toolCalls\":[" + CLASSIFY + "]}", Set.of("search_documentation"), 1);
    }
    @Test void rejectsUnknownArgumentsAndChangesToTheSubmittedIssue() {
        var injected = CLASSIFY.replace("\"body\":", "\"ownerId\":\"bob\",\"body\":");
        assertDenied("INVALID_TOOL_ARGUMENTS", "{\"toolCalls\":[" + injected + "]}", Set.of("classify_issue"), 1);
        var changed = CLASSIFY.replace("Cannot log in", "Another user's issue");
        assertDenied("TOOL_CONTEXT_MISMATCH", "{\"toolCalls\":[" + changed + "]}", Set.of("classify_issue"), 1);
    }
    @Test void deniesRoleSpoofingAndBoundsTheWholeBatchBeforeExecution() {
        assertThatThrownBy(() -> policy.validate("{\"toolCalls\":[" + CLASSIFY + "]}", Set.of("classify_issue"), 1,
                new ToolRegistry.Context("GUEST", "Cannot log in", "Password reset failed", "ACCOUNT")))
                .hasMessage("TOOL_ROLE_DENIED");
        assertDenied("TOOL_BUDGET_EXHAUSTED", "{\"toolCalls\":[" + CLASSIFY + "," + CLASSIFY + "]}", Set.of("classify_issue"), 1);
        assertDenied("TOOL_BUDGET_EXHAUSTED", "{\"toolCalls\":[" + CLASSIFY + "]}", Set.of("classify_issue"), 0);
    }
    @Test void rejectsOversizedResponsesWithoutParsingThem() {
        assertDenied("PROPOSAL_TOO_LARGE", " ".repeat(16_385), Set.of("classify_issue"), 1);
    }
    @Test void documentationLookupCannotChangeCategoryOrAcceptDestinations() {
        String valid = "{\"toolCalls\":[{\"tool\":\"search_documentation\",\"arguments\":{\"category\":\"ACCOUNT\"}}]}";
        assertThat(policy.validate(valid, Set.of("search_documentation"), 1, context).calls()).hasSize(1);
        assertDenied("TOOL_CONTEXT_MISMATCH", valid.replace("ACCOUNT", "BILLING"), Set.of("search_documentation"), 1);
        assertDenied("INVALID_TOOL_ARGUMENTS", valid.replace("\"category\":", "\"url\":\"http://127.0.0.1/admin\",\"category\":"), Set.of("search_documentation"), 1);
    }
    @ParameterizedTest @ValueSource(strings = {"", " ", "x"})
    void argumentValidationRejectsBlankOrOversizedInput(String input) {
        String body = input.equals("x") ? "x".repeat(4001) : input;
        var call = json.createObjectNode().put("tool", "classify_issue");
        call.putObject("arguments").put("subject", "Cannot log in").put("body", body);
        assertDenied("INVALID_TOOL_ARGUMENTS", "{\"toolCalls\":[" + call + "]}", Set.of("classify_issue"), 1);
    }
    private void assertDenied(String code, String raw, Set<String> allowed, int budget) {
        assertThatThrownBy(() -> policy.validate(raw, allowed, budget, context)).isInstanceOf(ToolRegistry.Denied.class).hasMessage(code);
    }
}
