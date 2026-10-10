package com.agentflow.execution;

import com.agentflow.workflow.GraphValidator;
import com.agentflow.workflow.WorkflowDefinition;
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
            if (node.type() == NodeType.TRIGGER) {
                if (node.operation() != null && node.operation() != Operation.RECEIVE_INPUT)
                    throw new IllegalArgumentException("Trigger operation must receive input");
            } else if (node.type() != NodeType.ACTION || node.operation() == null || node.operation() == Operation.RECEIVE_INPUT) {
                throw new IllegalArgumentException("Only explicit local actions are executable in this phase");
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
        return List.copyOf(ordered);
    }
}
