package com.agentflow.agent;

import com.agentflow.workflow.WorkflowDefinition.AgentGoal;
import java.util.Set;

/** Proposals only. A planner receives no executor, credentials, principal or approval capability. */
public interface Planner {
    String id();
    String propose(Request request);
    record Request(AgentGoal goal, String subject, String body, String category, Set<String> allowedTools) {
        public Request { allowedTools = Set.copyOf(allowedTools); }
    }
}
