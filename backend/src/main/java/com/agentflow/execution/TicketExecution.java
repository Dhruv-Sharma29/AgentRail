package com.agentflow.execution;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="ticket_executions")
class TicketExecution {
    @Id UUID id;
    @Column(nullable=false,updatable=false) UUID runId;
    @Column(nullable=false,updatable=false,length=64) String nodeId;
    @Column(nullable=false,updatable=false,columnDefinition="text") String arguments;
    @Column(nullable=false,length=20) String status;
    @Column(nullable=false,updatable=false) Instant createdAt;
    @Column(nullable=false,updatable=false) Instant deadline;
    Instant finishedAt;
    Integer issueNumber;
    @Column(length=300) String issueUrl;
    @Column(length=64) String errorCode;
    protected TicketExecution() {}
    TicketExecution(NodeExecution step,String arguments,Instant now,Instant deadline) {
        id=step.id; runId=step.runId; nodeId=step.nodeId; this.arguments=arguments;
        status="SENDING"; createdAt=now; this.deadline=deadline;
    }
    ExecutionDtos.Ticket view() { return new ExecutionDtos.Ticket(nodeId,status,createdAt,deadline,finishedAt,issueNumber,issueUrl,errorCode); }
}
