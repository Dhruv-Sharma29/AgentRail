package com.agentflow.execution;

import com.agentflow.workflow.WorkflowDefinition.Operation;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class ExecutionDtos {
    private ExecutionDtos() {}
    public record IssueInput(@NotBlank @Size(max = 200) String subject, @NotBlank @Size(max = 4000) String body) {}
    public record Start(@NotNull @Min(1) Integer versionNumber,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,100}") String idempotencyKey, @NotNull @Valid IssueInput input) {}
    public record Summary(UUID id, UUID workflowId, UUID versionId, int versionNumber, RunStatus status,
            Instant createdAt, Instant startedAt, Instant finishedAt, String errorCode) {}
    public record Details(UUID id, UUID workflowId, UUID versionId, int versionNumber, String mode, RunStatus status,
            Instant createdAt, Instant startedAt, Instant finishedAt, String errorCode,
            JsonNode input, JsonNode context, List<Step> steps, List<Event> events, int plannerCalls, int toolCalls,
            int reservedPlannerTokens, int plannerTokens, List<com.agentflow.approvals.ApprovalDtos.View> approvals, List<Ticket> tickets) {}
    public record Ticket(String nodeId,String status,Instant createdAt,Instant deadline,Instant finishedAt,Integer issueNumber,String issueUrl,String errorCode) {}
    public record Step(String nodeId, int ordinal, Operation operation, RunStatus status, int attempts,
            JsonNode input, JsonNode output, String errorCode, Instant startedAt, Instant finishedAt,
            String plannerId, JsonNode proposal, String policyStatus, String decisionCode, String planningState, Instant planningDeadline,
            Instant nextAttemptAt) {}
    public record Event(int sequence, String nodeId, String eventType, Instant occurredAt, String toolName, String decisionCode) {}
}
