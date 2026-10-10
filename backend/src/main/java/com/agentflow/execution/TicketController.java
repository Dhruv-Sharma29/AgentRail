package com.agentflow.execution;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;
@RestController
class TicketController {
    record Reconcile(@NotNull @Min(1) Integer issueNumber) {}
    private final TicketCoordinator tickets;
    private final ExecutionService executions;
    TicketController(TicketCoordinator tickets,ExecutionService executions) { this.tickets=tickets; this.executions=executions; }
    @PostMapping("/api/runs/{run}/ticket/reconcile")
    ExecutionDtos.Details reconcile(Principal principal,@PathVariable UUID run,@Valid @RequestBody Reconcile request) {
        tickets.reconcile(principal.getName(),run,request.issueNumber()); return executions.get(principal.getName(),run);
    }
}
