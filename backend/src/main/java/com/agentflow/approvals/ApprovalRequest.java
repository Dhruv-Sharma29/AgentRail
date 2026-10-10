package com.agentflow.approvals;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Entity @Table(name="approval_requests")
public class ApprovalRequest {
    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED, EXPIRED }
    @Id private UUID id;
    @Column(nullable=false,updatable=false) private UUID runId;
    @Column(nullable=false,updatable=false) private UUID ownerId;
    @Column(nullable=false,updatable=false) private UUID nodeExecutionId;
    @Column(nullable=false,updatable=false) private UUID targetNodeExecutionId;
    @Column(nullable=false,updatable=false,length=64) private String nodeId;
    @Column(nullable=false,updatable=false,length=64) private String targetNodeId;
    @Column(nullable=false,updatable=false,length=30) private String requiredRole;
    @Column(nullable=false,updatable=false,columnDefinition="text") private String payload;
    @Column(nullable=false,updatable=false,length=64) private String payloadDigest;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    @Column(nullable=false,updatable=false) private Instant expiresAt;
    private Instant decidedAt;
    private UUID decidedBy;
    protected ApprovalRequest() {}
    public ApprovalRequest(UUID runId,UUID ownerId,UUID step,UUID target,String node,String targetNode,String payload,Instant now,Instant expiry) {
        id=UUID.randomUUID(); this.runId=runId; this.ownerId=ownerId; nodeExecutionId=step; targetNodeExecutionId=target;
        nodeId=node; targetNodeId=targetNode; requiredRole="USER"; this.payload=payload; payloadDigest=digest(payload);
        status=Status.PENDING; createdAt=now; expiresAt=expiry;
    }
    public void decide(Status next,UUID actor,Instant now) {
        if(status!=Status.PENDING || next==Status.PENDING) throw new IllegalStateException("Approval already resolved");
        status=next; decidedBy=actor; decidedAt=now;
    }
    public static String digest(String payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public UUID id(){return id;} public UUID runId(){return runId;} public UUID ownerId(){return ownerId;}
    public UUID nodeExecutionId(){return nodeExecutionId;} public UUID targetNodeExecutionId(){return targetNodeExecutionId;}
    public String nodeId(){return nodeId;} public String targetNodeId(){return targetNodeId;} public String requiredRole(){return requiredRole;}
    public String payload(){return payload;} public String payloadDigest(){return payloadDigest;} public Status status(){return status;}
    public Instant createdAt(){return createdAt;} public Instant expiresAt(){return expiresAt;} public Instant decidedAt(){return decidedAt;}
    public UUID decidedBy(){return decidedBy;}
}
