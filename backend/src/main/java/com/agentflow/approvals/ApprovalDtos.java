package com.agentflow.approvals;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ApprovalDtos {
    private ApprovalDtos() {}
    public enum Decision { APPROVE, REJECT }
    public record Decide(@NotNull Decision decision,@NotBlank @Pattern(regexp="[0-9a-f]{64}") String payloadDigest) {}
    public record View(UUID id,UUID runId,String nodeId,String targetNodeId,String requiredRole,ApprovalRequest.Status status,
            JsonNode payload,String payloadDigest,Instant createdAt,Instant expiresAt,Instant decidedAt,UUID decidedBy) {}
}
