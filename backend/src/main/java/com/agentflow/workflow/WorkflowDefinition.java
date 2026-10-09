package com.agentflow.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record WorkflowDefinition(
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid Node> nodes,
        @NotNull @Size(max = 100) List<@NotNull @Valid Edge> edges) {
    public enum NodeType { TRIGGER, AI_STEP, CONDITION, APPROVAL, ACTION }

    public record Node(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String id,
            @NotNull NodeType type,
            @NotBlank @Size(max = 120) String label) {}

    public record Edge(@NotBlank String source, @NotBlank String target) {}
}
