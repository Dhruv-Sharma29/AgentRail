package com.agentflow.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record WorkflowDefinition(
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid Node> nodes,
        @NotNull @Size(max = 100) List<@NotNull @Valid Edge> edges) {
    public enum NodeType { TRIGGER, AI_STEP, CONDITION, APPROVAL, ACTION }
    public enum Operation { RECEIVE_INPUT, CLASSIFY_ISSUE, SEARCH_DOCUMENTATION, RECORD_RESULT }
    public enum AgentGoal { CLASSIFY_ISSUE, FIND_DOCUMENTATION }
    public record AgentSettings(@NotNull AgentGoal goal,
            @NotNull @Size(min = 1, max = 2) List<@NotBlank @Pattern(regexp = "[a-z_]{1,64}") String> allowedTools,
            @NotNull @Min(1) @Max(3) Integer maxToolCalls) {}

    public record Node(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String id,
            @NotNull NodeType type,
            @NotBlank @Size(max = 120) String label,
            Operation operation,
            @Valid AgentSettings agent) {
        public Node(String id, NodeType type, String label) { this(id, type, label, null, null); }
        public Node(String id, NodeType type, String label, Operation operation) { this(id, type, label, operation, null); }
    }

    public record Edge(@NotBlank String source, @NotBlank String target) {}
}
