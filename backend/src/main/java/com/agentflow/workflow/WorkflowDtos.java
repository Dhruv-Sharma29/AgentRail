package com.agentflow.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkflowDtos {
    private WorkflowDtos() {}
    public record Create(@NotBlank @Size(max = 120) String name,
            @NotNull @Size(max = 2000) String description, @NotNull @Valid WorkflowDefinition definition) {}
    public record Update(@NotBlank @Size(max = 120) String name,
            @NotNull @Size(max = 2000) String description, @NotNull @Valid WorkflowDefinition definition,
            @NotNull @Min(1) Integer expectedVersion) {}
    public record Details(UUID id, String name, String description, int currentVersion, boolean archived,
            Instant createdAt, Instant updatedAt, WorkflowDefinition definition) {}
    public record Summary(UUID id, String name, String description, int currentVersion, Instant updatedAt) {}
    public record Version(UUID id, int number, String name, String description, WorkflowDefinition definition, Instant createdAt) {}
    public record PageResult<T>(List<T> items, long total, int page, int size) {}
}
