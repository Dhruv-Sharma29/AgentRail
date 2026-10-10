package com.agentflow.agent;

import java.time.Instant;
import java.util.UUID;

/** Network boundary: callers must dispatch after the durable claim transaction commits. */
public interface LivePlanner {
    int TOKEN_RESERVATION = 18_000;
    int MAX_RUN_TOKENS = 90_000;
    record Job(UUID intentId, UUID runId, String nodeId, String provider, String model, Planner.Request request, Instant deadline) {}
    record Result(String proposal, int totalTokens) {}
    enum ErrorCode { PLANNER_FAILURE, PLANNER_TIMEOUT, PLANNER_AUTHENTICATION, PLANNER_RATE_LIMIT, PLANNER_QUOTA, PLANNER_ACCESS_LIMIT, PLANNER_UNAVAILABLE }
    class Failure extends RuntimeException {
        public Failure(ErrorCode code) { super(code.name()); }
    }
    Result propose(Job job);
}
