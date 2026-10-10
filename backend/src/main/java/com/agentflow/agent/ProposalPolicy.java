package com.agentflow.agent;

import com.agentflow.tools.ToolRegistry;
import com.agentflow.tools.ToolRegistry.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class ProposalPolicy {
    public static final int MAX_PLANNER_CALLS = 5;
    public static final int MAX_RUN_TOOL_CALLS = 10;
    public static final int MAX_ELAPSED_SECONDS = 120;
    private final ObjectMapper json;
    private final ToolRegistry registry;
    public ProposalPolicy(ObjectMapper json, ToolRegistry registry) { this.json = json; this.registry = registry; }
    public record ToolCall(String tool, JsonNode arguments) {}
    public record Proposal(List<ToolCall> toolCalls) {}
    public record Decision(String canonicalProposal, List<ValidatedCall> calls) {}

    public Decision validate(String raw, Set<String> allowed, int budget, Context context) {
        if (raw == null) throw new Denied("INVALID_PROPOSAL");
        if (raw.length() > 16_384 || raw.getBytes(StandardCharsets.UTF_8).length > 16_384) throw new Denied("PROPOSAL_TOO_LARGE");
        Proposal proposal;
        try {
            proposal = json.readerFor(Proposal.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readValue(raw);
        } catch (Exception invalid) { throw new Denied("INVALID_PROPOSAL"); }
        if (proposal == null || proposal.toolCalls() == null || proposal.toolCalls().isEmpty()
                || proposal.toolCalls().stream().anyMatch(Objects::isNull)) throw new Denied("INVALID_PROPOSAL");
        if (proposal.toolCalls().size() > Math.min(3, budget)) throw new Denied("TOOL_BUDGET_EXHAUSTED");
        var approved = new ArrayList<ValidatedCall>();
        for (var call : proposal.toolCalls()) {
            if (call.tool() == null || call.tool().isBlank() || call.arguments() == null || !call.arguments().isObject()) throw new Denied("INVALID_PROPOSAL");
            if (!ToolRegistry.names().contains(call.tool())) throw new Denied("UNKNOWN_TOOL");
            if (!allowed.contains(call.tool())) throw new Denied("TOOL_NOT_ALLOWED");
            approved.add(registry.validate(call.tool(), call.arguments().toString(), context));
        }
        try { return new Decision(json.writeValueAsString(proposal), List.copyOf(approved)); }
        catch (Exception invalid) { throw new Denied("INVALID_PROPOSAL"); }
    }
}
