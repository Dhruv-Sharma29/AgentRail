package com.agentflow.agent;

import java.time.Instant;
import java.util.UUID;

/** Network boundary: callers must dispatch after the durable claim transaction commits. */
public interface LivePlanner {
    int TOKEN_RESERVATION = 18_000;
    int MAX_RUN_TOKENS = 90_000;
    record Job(UUID intentId, UUID runId, String nodeId, String model, Planner.Request request, Instant deadline) {}
    record Result(String proposal, int totalTokens) {}
    Result propose(Job job);
}
