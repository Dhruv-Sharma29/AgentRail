package com.agentflow.execution;

import com.agentflow.workflow.WorkflowDtos.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.util.UUID;
import static com.agentflow.execution.ExecutionDtos.*;

@RestController
public class ExecutionController {
    private final ExecutionService service;
    public ExecutionController(ExecutionService service) { this.service = service; }
    @PostMapping("/api/workflows/{id}/runs")
    ResponseEntity<Details> start(Principal user, @PathVariable UUID id, @Valid @RequestBody Start request) {
        var run = service.start(user.getName(), id, request);
        return ResponseEntity.accepted().location(URI.create("/api/runs/" + run.id())).body(run);
    }
    @GetMapping("/api/workflows/{id}/runs")
    PageResult<Summary> history(Principal user, @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.history(user.getName(), id, page, size);
    }
    @GetMapping("/api/runs/{id}") Details get(Principal user, @PathVariable UUID id) { return service.get(user.getName(), id); }
    @PostMapping("/api/runs/{id}/cancel") Details cancel(Principal user, @PathVariable UUID id) { return service.cancel(user.getName(), id); }
}
