package com.agentflow.workflow;

import org.springframework.stereotype.Component;
import java.util.*;
import static com.agentflow.workflow.WorkflowDefinition.NodeType.TRIGGER;

@Component
public class GraphValidator {
    public void validate(WorkflowDefinition definition) {
        if (definition == null || definition.nodes() == null || definition.edges() == null
                || definition.nodes().isEmpty() || definition.nodes().size() > 50 || definition.edges().size() > 100) {
            throw new IllegalArgumentException("A workflow requires 1–50 nodes and at most 100 edges");
        }
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, Integer> incoming = new HashMap<>();
        String trigger = null;
        for (var node : definition.nodes()) {
            if (node == null || node.id() == null || !node.id().matches("[a-zA-Z0-9_-]{1,64}")
                    || node.type() == null || node.label() == null || node.label().isBlank() || node.label().length() > 120) {
                throw new IllegalArgumentException("Invalid workflow node");
            }
            if (outgoing.putIfAbsent(node.id(), new ArrayList<>()) != null) {
                throw new IllegalArgumentException("Node IDs must be unique");
            }
            incoming.put(node.id(), 0);
            if (node.type() == TRIGGER) {
                if (trigger != null) throw new IllegalArgumentException("Exactly one trigger is required");
                trigger = node.id();
            }
        }
        if (trigger == null) throw new IllegalArgumentException("Exactly one trigger is required");
        Set<WorkflowDefinition.Edge> uniqueEdges = new HashSet<>();
        for (var edge : definition.edges()) {
            if (edge == null || !outgoing.containsKey(edge.source()) || !outgoing.containsKey(edge.target())) {
                throw new IllegalArgumentException("Edges must reference existing nodes");
            }
            if (edge.source().equals(edge.target()) || !uniqueEdges.add(edge)) {
                throw new IllegalArgumentException("Self connections and duplicate edges are forbidden");
            }
            outgoing.get(edge.source()).add(edge.target());
            incoming.compute(edge.target(), (id, count) -> count + 1);
        }
        if (incoming.get(trigger) != 0) throw new IllegalArgumentException("The trigger cannot have incoming edges");

        Queue<String> queue = new ArrayDeque<>();
        incoming.forEach((id, count) -> { if (count == 0) queue.add(id); });
        int visited = 0;
        while (!queue.isEmpty()) {
            String id = queue.remove();
            visited++;
            for (String target : outgoing.get(id)) {
                if (incoming.compute(target, (key, count) -> count - 1) == 0) queue.add(target);
            }
        }
        if (visited != outgoing.size()) throw new IllegalArgumentException("Workflow cycles are forbidden");

        Set<String> reachable = new HashSet<>();
        queue.add(trigger);
        while (!queue.isEmpty()) {
            String id = queue.remove();
            if (reachable.add(id)) queue.addAll(outgoing.get(id));
        }
        if (reachable.size() != outgoing.size()) throw new IllegalArgumentException("Every node must be reachable from the trigger");
    }
}
