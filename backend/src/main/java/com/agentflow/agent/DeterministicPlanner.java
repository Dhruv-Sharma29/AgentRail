package com.agentflow.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class DeterministicPlanner implements Planner {
    private final ObjectMapper json;
    public DeterministicPlanner(ObjectMapper json) { this.json = json; }
    @Override public String id() { return "agentflow-deterministic-v1"; }
    @Override public String propose(Request request) {
        var proposal = json.createObjectNode();
        var call = proposal.putArray("toolCalls").addObject();
        var arguments = call.putObject("arguments");
        switch (request.goal()) {
            case CLASSIFY_ISSUE -> {
                call.put("tool", "classify_issue");
                arguments.put("subject", request.subject()); arguments.put("body", request.body());
            }
            case FIND_DOCUMENTATION -> {
                call.put("tool", "search_documentation"); arguments.put("category", request.category());
            }
        }
        return proposal.toString();
    }
}
