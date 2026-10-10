package com.agentflow.execution;

public enum RunStatus {
    PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED;
    public boolean terminal() { return this == SUCCEEDED || this == FAILED || this == CANCELLED; }
}
