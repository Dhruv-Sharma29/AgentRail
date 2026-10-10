package com.agentflow.execution;

import com.agentflow.workflow.WorkflowDefinition.Operation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import java.util.Locale;
import static com.agentflow.execution.ExecutionDtos.IssueInput;

/** Bounded, pure local handlers. Remote effects require a separate durable tool boundary. */
@Component
public class LocalActions {
    private final ObjectMapper json;
    public LocalActions(ObjectMapper json) { this.json = json; }

    public ObjectNode execute(Operation operation, IssueInput input, ObjectNode state) {
        var next = state.deepCopy();
        switch (operation) {
            case RECEIVE_INPUT -> next.set("issue", json.valueToTree(input));
            case CLASSIFY_ISSUE -> {
                require(next.has("issue"), "MISSING_ISSUE");
                String text = (input.subject() + " " + input.body()).toLowerCase(Locale.ROOT);
                String category = text.contains("password") || text.contains("log in") || text.contains("login") ? "ACCOUNT"
                        : text.contains("payment") || text.contains("billing") || text.contains("invoice") ? "BILLING" : "GENERAL";
                var classification = next.putObject("classification");
                classification.put("category", category);
                classification.put("priority", text.contains("urgent") || text.contains("outage") ? "HIGH" : "NORMAL");
                classification.put("explanation", "Matched local keyword rules; no model was called.");
            }
            case SEARCH_DOCUMENTATION -> {
                require(next.has("classification"), "MISSING_CLASSIFICATION");
                String category = next.path("classification").path("category").asText();
                var document = next.putArray("documents").addObject();
                switch (category) {
                    case "ACCOUNT" -> {
                        document.put("id", "account-access"); document.put("title", "Account access");
                        document.put("excerpt", "Use the password reset flow and verify the account email.");
                    }
                    case "BILLING" -> {
                        document.put("id", "billing-help"); document.put("title", "Billing help");
                        document.put("excerpt", "Check invoice status and payment details with the billing team.");
                    }
                    default -> {
                        document.put("id", "support-intake"); document.put("title", "Support intake");
                        document.put("excerpt", "Include reproduction steps, expected behavior and observed behavior.");
                    }
                }
            }
            case RECORD_RESULT -> {
                require(next.has("classification") && next.has("documents"), "MISSING_SUPPORT_CONTEXT");
                var result = next.putObject("result");
                result.put("subject", input.subject());
                result.set("classification", next.get("classification").deepCopy());
                result.set("documents", next.get("documents").deepCopy());
                result.put("externalTicketCreated", false);
            }
        }
        return next;
    }
    private static void require(boolean valid, String code) { if (!valid) throw new StepFailure(code); }
    public static class StepFailure extends RuntimeException {
        StepFailure(String code) { super(code); }
    }
}
