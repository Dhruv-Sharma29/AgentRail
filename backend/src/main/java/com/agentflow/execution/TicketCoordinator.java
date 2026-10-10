package com.agentflow.execution;

import com.agentflow.tools.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

@Component
public class TicketCoordinator {
    private final TicketExecutionService service;
    private final GitHubTicketClient client;
    public TicketCoordinator(TicketExecutionService service,GitHubTicketClient client) { this.service=service; this.client=client; }
    public boolean advance(UUID run) {
        noTransaction(); var claim=service.claim(run); if(claim.job()==null) return claim.handled();
        var job=claim.job(); GitHubTicketClient.Result result=null; String failure=null;
        try { result=client.create(job.arguments(),job.deadline()); }
        catch(GitHubTicketClient.Failure safe) { failure=safe.getMessage(); }
        catch(ToolRegistry.Denied denied) { failure="TICKET_NOT_DISPATCHED"; }
        catch(RuntimeException unknown) { failure="TICKET_OUTCOME_UNKNOWN"; }
        service.complete(job,result,failure); return true;
    }
    public void reconcile(String username,UUID run,int number) {
        noTransaction();
        try {
            var job=service.reconciliation(username,run,number); if(job==null) return;
            var result=client.find(job.arguments(),number); service.reconciled(job,result);
        }
        catch(GitHubTicketClient.Failure failure) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Issue does not confirm the frozen action"); }
        catch(ToolRegistry.Denied failure) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Ticket policy changed; reconciliation denied"); }
    }
    private static void noTransaction() { if(TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Ticket dispatch requires no transaction"); }
}
