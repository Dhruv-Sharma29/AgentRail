package com.agentflow.execution;

import com.agentflow.workflow.GraphValidator;
import com.agentflow.workflow.WorkflowDefinition;
import com.agentflow.tools.ToolRegistry;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.agentflow.workflow.WorkflowDefinition.*;

@Component
public class ExecutionPlan {
    private final GraphValidator graphs;
    public ExecutionPlan(GraphValidator graphs) { this.graphs = graphs; }

    public List<Node> validate(WorkflowDefinition definition) {
        graphs.validate(definition);
        var byId = new HashMap<String, Node>();
        for (var node : definition.nodes()) {
            byId.put(node.id(), node);
            if (node.operation() == Operation.CREATE_TICKET) {
                if (node.type() != NodeType.ACTION || node.ticket() == null || node.ticket().repository() == null
                        || !node.ticket().repository().matches(com.agentflow.tools.TicketPolicy.REPOSITORY_PATTERN))
                    throw new IllegalArgumentException("Ticket creation requires a configured action destination");
            } else if (node.ticket() != null) throw new IllegalArgumentException("Only ticket actions may configure a destination");
            if (node.type() != NodeType.APPROVAL && node.approval() != null)
                throw new IllegalArgumentException("Only approval nodes may configure a checkpoint");
            if (node.type() == NodeType.TRIGGER) {
                if (node.agent() != null || (node.operation() != null && node.operation() != Operation.RECEIVE_INPUT))
                    throw new IllegalArgumentException("Trigger operation must receive input");
            } else if (node.type() == NodeType.AI_STEP) {
                var settings = node.agent();
                if (node.operation() != null || settings == null || settings.goal() == null
                        || settings.allowedTools() == null || settings.allowedTools().isEmpty() || settings.allowedTools().size() > 2
                        || settings.allowedTools().stream().anyMatch(t -> t == null || !ToolRegistry.names().contains(t))
                        || new HashSet<>(settings.allowedTools()).size() != settings.allowedTools().size()
                        || settings.maxToolCalls() == null || settings.maxToolCalls() < 1 || settings.maxToolCalls() > 3)
                    throw new IllegalArgumentException("Agent steps require registered tools and bounded settings");
            } else if (node.type() == NodeType.APPROVAL) {
                var settings=node.approval();
                if (node.operation()!=null || node.agent()!=null || settings==null || settings.timeoutSeconds()==null
                        || settings.timeoutSeconds()<60 || settings.timeoutSeconds()>86400)
                    throw new IllegalArgumentException("Approval requires a bounded deadline");
            } else if (node.type() != NodeType.ACTION || node.agent() != null || node.operation() == null || node.operation() == Operation.RECEIVE_INPUT) {
                throw new IllegalArgumentException("Only configured agent steps and explicit local actions are executable");
            }
        }
        var next = new HashMap<String, String>();
        for (var edge : definition.edges()) {
            if (next.put(edge.source(), edge.target()) != null)
                throw new IllegalArgumentException("Execution currently requires a linear workflow");
        }
        var ordered = new ArrayList<Node>();
        var node = definition.nodes().stream().filter(n -> n.type() == NodeType.TRIGGER).findFirst().orElseThrow();
        while (node != null) {
            ordered.add(node);
            node = byId.get(next.get(node.id()));
        }
        if (ordered.size() != definition.nodes().size()) throw new IllegalArgumentException("Execution requires one chain");
        for (int i=0;i<ordered.size();i++) if (ordered.get(i).type()==NodeType.APPROVAL) {
            if (i+1>=ordered.size() || ordered.get(i+1).type()!=NodeType.ACTION
                    || !Set.of(Operation.RECORD_RESULT,Operation.CREATE_TICKET).contains(ordered.get(i+1).operation()))
                throw new IllegalArgumentException("Approval must immediately precede a result or ticket action");
        }
        for (int i=0;i<ordered.size();i++) if (ordered.get(i).operation()==Operation.CREATE_TICKET
                && (i==0 || ordered.get(i-1).type()!=NodeType.APPROVAL || i!=ordered.size()-1))
            throw new IllegalArgumentException("Ticket creation must be the final action immediately after approval");
        return List.copyOf(ordered);
    }
}
