package com.agentflow.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflows")
public class Workflow {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private UUID ownerId;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 2000) private String description;
    @Column(nullable = false) private int currentVersion;
    @Column(nullable = false) private boolean archived;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long revision;

    protected Workflow() {}
    Workflow(UUID ownerId, String name, String description) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.name = name.strip();
        this.description = description;
        this.currentVersion = 1;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }
    void update(String name, String description) {
        this.name = name.strip();
        this.description = description;
        currentVersion++;
        updatedAt = Instant.now();
    }
    void archive() { archived = true; updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getCurrentVersion() { return currentVersion; }
    public boolean isArchived() { return archived; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
