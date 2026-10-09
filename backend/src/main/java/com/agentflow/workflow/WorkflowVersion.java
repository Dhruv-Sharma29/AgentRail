package com.agentflow.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_versions", uniqueConstraints = @UniqueConstraint(columnNames = {"workflow_id", "number"}))
public class WorkflowVersion {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private UUID workflowId;
    @Column(nullable = false, updatable = false) private int number;
    @Column(nullable = false, updatable = false, length = 120) private String name;
    @Column(nullable = false, updatable = false, length = 2000) private String description;
    @Column(nullable = false, updatable = false, columnDefinition = "text") private String definition;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected WorkflowVersion() {}
    WorkflowVersion(Workflow workflow, String definition) {
        id = UUID.randomUUID();
        workflowId = workflow.getId();
        number = workflow.getCurrentVersion();
        name = workflow.getName();
        description = workflow.getDescription();
        this.definition = definition;
        createdAt = workflow.getUpdatedAt();
    }
    public UUID getId() { return id; }
    public int getNumber() { return number; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getDefinition() { return definition; }
    public Instant getCreatedAt() { return createdAt; }
}
