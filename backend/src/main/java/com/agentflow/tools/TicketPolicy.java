package com.agentflow.tools;

import com.agentflow.execution.ExecutionDtos.IssueInput;
import com.agentflow.workflow.WorkflowDefinition.TicketSettings;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.*;

/** Code-owned proposal: no model can choose a destination or grant approval. */
@Component
public class TicketPolicy {
    public static final String REPOSITORY_PATTERN = "[a-zA-Z0-9][a-zA-Z0-9-]{0,38}/[a-zA-Z0-9][a-zA-Z0-9_.-]{0,99}";
    public record Arguments(String repository, String title, String body) {}
    private final GitHubTicketConfig config;
    public TicketPolicy(GitHubTicketConfig config) { this.config = config; }
    public Arguments propose(UUID executionId, TicketSettings settings, IssueInput issue, JsonNode context) {
        String category = context.path("classification").path("category").asText();
        String priority = context.path("classification").path("priority").asText();
        if (!Set.of("ACCOUNT", "BILLING", "GENERAL").contains(category) || !Set.of("HIGH", "NORMAL").contains(priority))
            throw new ToolRegistry.Denied("MISSING_SUPPORT_CONTEXT");
        var args = new Arguments(settings == null ? null : settings.repository(), issue.subject(),
                issue.body() + "\n\n---\nAgentFlow classification: " + category + " / " + priority
                        + "\n\n<!-- agentflow-ticket:" + executionId + " -->");
        validate(args, "USER"); return args;
    }
    public void validate(Arguments args, String role) {
        if (!"USER".equals(role)) throw new ToolRegistry.Denied("TOOL_ROLE_DENIED");
        if (!config.ready()) throw new ToolRegistry.Denied("TICKET_CONNECTOR_DISABLED");
        if (args == null || args.repository() == null || !args.repository().matches(REPOSITORY_PATTERN)
                || !args.repository().equals(config.repository())) throw new ToolRegistry.Denied("TICKET_DESTINATION_DENIED");
        if (args.title() == null || args.title().isBlank() || args.title().length() > 200
                || args.body() == null || args.body().isBlank() || args.body().length() > 8000)
            throw new ToolRegistry.Denied("INVALID_TOOL_ARGUMENTS");
    }
}
