package com.agentflow.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.stream.Stream;
import static com.agentflow.workflow.WorkflowDefinition.*;
import static org.assertj.core.api.Assertions.*;

class GraphValidatorTest {
    private final GraphValidator validator = new GraphValidator();

    @Test
    void acceptsAConnectedAcyclicGraphWithOneTrigger() {
        assertThatCode(() -> validator.validate(valid())).doesNotThrowAnyException();
    }

    @Test
    void acceptsBranchesThatMergeIntoOneAction() {
        var nodes = List.of(node("start", NodeType.TRIGGER), node("left", NodeType.CONDITION),
                node("right", NodeType.AI_STEP), node("end", NodeType.ACTION));
        var edges = List.of(new Edge("start", "left"), new Edge("start", "right"),
                new Edge("left", "end"), new Edge("right", "end"));
        assertThatCode(() -> validator.validate(new WorkflowDefinition(nodes, edges))).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "rejects invalid graph {index}")
    @MethodSource("invalidGraphs")
    void rejectsUnsafeGraphStructures(WorkflowDefinition definition) {
        assertThatThrownBy(() -> validator.validate(definition)).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<WorkflowDefinition> invalidGraphs() {
        var start = node("start", NodeType.TRIGGER);
        var end = node("end", NodeType.ACTION);
        return Stream.of(
                new WorkflowDefinition(List.of(), List.of()),
                new WorkflowDefinition(List.of(start, start), List.of()),
                new WorkflowDefinition(List.of(end), List.of()),
                new WorkflowDefinition(List.of(start, node("other", NodeType.TRIGGER)), List.of(new Edge("start", "other"))),
                new WorkflowDefinition(List.of(start, end), List.of(new Edge("start", "missing"))),
                new WorkflowDefinition(List.of(start, end), List.of(new Edge("start", "end"), new Edge("start", "end"))),
                new WorkflowDefinition(List.of(start, end), List.of(new Edge("start", "start"))),
                new WorkflowDefinition(List.of(start, end), List.of()),
                new WorkflowDefinition(List.of(start, end), List.of(new Edge("start", "end"), new Edge("end", "start"))),
                new WorkflowDefinition(List.of(start, end, node("loop", NodeType.CONDITION)),
                        List.of(new Edge("start", "end"), new Edge("end", "loop"), new Edge("loop", "end"))),
                new WorkflowDefinition(null, List.of()),
                new WorkflowDefinition(List.of(start, end), null));
    }

    static Node node(String id, NodeType type) { return new Node(id, type, id); }
    static WorkflowDefinition valid() {
        return new WorkflowDefinition(List.of(node("start", NodeType.TRIGGER), node("end", NodeType.ACTION)),
                List.of(new Edge("start", "end")));
    }
}
