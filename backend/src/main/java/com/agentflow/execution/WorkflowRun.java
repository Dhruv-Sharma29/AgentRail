package com.agentflow.execution;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_runs")
class WorkflowRun {
    @Id UUID id;
    @Column(nullable = false, updatable = false) UUID workflowId;
    @Column(nullable = false, updatable = false) UUID versionId;
    @Column(nullable = false, updatable = false) int versionNumber;
    @Column(nullable = false, updatable = false) UUID ownerId;
    @Column(nullable = false, updatable = false, length = 100) String idempotencyKey;
    @Column(nullable = false, updatable = false, columnDefinition = "text") String input;
    @Column(nullable = false, columnDefinition = "text") String context;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) RunStatus status;
    @Column(length = 64) String errorCode;
    @Column(nullable = false, updatable = false, length = 32) String mode = "DETERMINISTIC";
    @Column(nullable = false) int plannerCalls;
    @Column(nullable = false) int toolCalls;
    @Column(updatable = false, length = 64) String plannerModel;
    @Column(nullable = false) int reservedPlannerTokens;
    @Column(nullable = false) int plannerTokens;
    @Column(nullable = false, updatable = false) Instant createdAt;
    Instant startedAt;
    Instant finishedAt;
    @Version long revision;

    protected WorkflowRun() {}
    WorkflowRun(UUID workflowId, UUID versionId, int versionNumber, UUID ownerId, String key, String input, Instant now) {
        id = UUID.randomUUID(); this.workflowId = workflowId; this.versionId = versionId;
        this.versionNumber = versionNumber; this.ownerId = ownerId; idempotencyKey = key;
        this.input = input; context = "{}"; status = RunStatus.PENDING; createdAt = now;
    }
    void start(Instant now) { status = RunStatus.RUNNING; startedAt = now; }
    void finish(RunStatus status, Instant now, String errorCode) {
        this.status = status; finishedAt = now; this.errorCode = errorCode;
    }
}
