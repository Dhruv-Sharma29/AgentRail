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
    private final TicketCoordinator tickets;
    public PlanningCoordinator(ExecutionService executions, ObjectProvider<LivePlanner> planners,TicketCoordinator tickets) {
        this.executions = executions; this.planners = planners;
        this.tickets=tickets;
    }
    public void advance(UUID run) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Planning dispatch requires no transaction");
        if(tickets.advance(run)) return;
        var claimed = executions.claimPlanning(run);
        if (claimed.isEmpty()) { executions.advance(run); return; }
        var job = claimed.get();
        LivePlanner.Result result = null;
        String failure = null;
        try {
            var planner = planners.getIfAvailable();
            if (planner == null) failure = "PLANNER_UNAVAILABLE";
            else result = planner.propose(job);
        } catch (LivePlanner.Failure exception) { failure = exception.getMessage(); }
        catch (RuntimeException exception) { failure = "PLANNER_FAILURE"; }
        executions.completePlanning(job, result, failure);
    }
}
