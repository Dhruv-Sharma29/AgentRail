package com.agentflow.execution;

import com.agentflow.agent.LivePlanner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;

@Component
public class PlanningCoordinator {
    private final ExecutionService executions;
    private final ObjectProvider<LivePlanner> planners;
    public PlanningCoordinator(ExecutionService executions, ObjectProvider<LivePlanner> planners) {
        this.executions = executions; this.planners = planners;
    }
    public void advance(UUID run) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Planning dispatch requires no transaction");
        var claimed = executions.claimPlanning(run);
        if (claimed.isEmpty()) { executions.advance(run); return; }
        var job = claimed.get();
        LivePlanner.Result result = null;
        String failure = null;
        try {
            var planner = planners.getIfAvailable();
            if (planner == null) failure = "PLANNER_UNAVAILABLE";
            else result = planner.propose(job);
        } catch (RuntimeException exception) { failure = "PLANNER_FAILURE"; }
        executions.completePlanning(job, result, failure);
    }
}
