package com.agentflow.execution;

import com.agentflow.workflow.WorkflowDefinition.Operation;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "node_executions")
class NodeExecution {
    @Id UUID id;
    @Column(nullable = false, updatable = false) UUID runId;
    @Column(nullable = false, updatable = false, length = 64) String nodeId;
    @Column(nullable = false, updatable = false) int ordinal;
    @Enumerated(EnumType.STRING) @Column(updatable = false, length = 40) Operation operation;
    @Column(updatable = false, columnDefinition = "text") String agentSettings;
    @Column(updatable = false, columnDefinition = "text") String approvalSettings;
    @Column(length = 64) String plannerId;
    @Column(columnDefinition = "text") String proposal;
    @Column(length = 20) String policyStatus;
    @Column(length = 64) String decisionCode;
    UUID planningIntentId;
    @Column(columnDefinition = "text") String planningRequest;
    Instant planningDeadline;
    @Column(length = 20) String planningState;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) RunStatus status;
    @Column(nullable = false) int attempts;
    @Column(columnDefinition = "text") String input;
    @Column(columnDefinition = "text") String output;
    @Column(length = 64) String errorCode;
    Instant startedAt;
    Instant finishedAt;
    Instant nextAttemptAt;

    protected NodeExecution() {}
    NodeExecution(UUID runId, String nodeId, int ordinal, Operation operation) {
        id = UUID.randomUUID(); this.runId = runId; this.nodeId = nodeId;
        this.ordinal = ordinal; this.operation = operation; status = RunStatus.PENDING;
    }
    void complete(String input, String output, Instant now) {
        this.input = input; this.output = output; attempts++; startedAt = now; finishedAt = now; status = RunStatus.SUCCEEDED;
        errorCode = null; nextAttemptAt = null;
    }
    void fail(String input, String code, Instant now) {
        this.input = input; errorCode = code;
        if (status != RunStatus.WAITING_FOR_APPROVAL) { attempts++; startedAt = now; }
        finishedAt = now; status = RunStatus.FAILED;
        nextAttemptAt = null;
    }
    void retry(String input, Instant now, Instant due) {
        this.input = input; attempts++; startedAt = now; finishedAt = now;
        errorCode = "LOCAL_TRANSIENT_FAILURE"; nextAttemptAt = due; status = RunStatus.PENDING;
    }
    void pause(String input, Instant now) { this.input=input; attempts++; startedAt=now; status=RunStatus.WAITING_FOR_APPROVAL; }
    void approved(Instant now) { output=input; finishedAt=now; status=RunStatus.SUCCEEDED; }
    void cancel(Instant now) { status = RunStatus.CANCELLED; finishedAt = now; nextAttemptAt = null; }
}
