package com.agentflow.execution;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
class RunEvent {
    @Id UUID id;
    @Column(nullable = false, updatable = false) UUID runId;
    @Column(nullable = false, updatable = false) int sequence;
    @Column(updatable = false, length = 64) String nodeId;
    @Column(nullable = false, updatable = false, length = 40) String eventType;
    @Column(nullable = false, updatable = false) Instant occurredAt;
    protected RunEvent() {}
    RunEvent(UUID runId, int sequence, String nodeId, String eventType, Instant now) {
        id = UUID.randomUUID(); this.runId = runId; this.sequence = sequence;
        this.nodeId = nodeId; this.eventType = eventType; occurredAt = now;
    }
}
