package com.agentflow.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.util.UUID;
import static com.agentflow.workflow.WorkflowDtos.*;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    private final WorkflowService service;
    public WorkflowController(WorkflowService service) { this.service = service; }
    @PostMapping ResponseEntity<Details> create(Principal user, @Valid @RequestBody Create request) {
        var workflow = service.create(user.getName(), request);
        return ResponseEntity.created(URI.create("/api/workflows/" + workflow.id())).body(workflow);
    }
    @GetMapping PageResult<Summary> list(Principal user, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(user.getName(), page, size);
    }
    @GetMapping("/{id}") Details get(Principal user, @PathVariable UUID id) { return service.get(user.getName(), id); }
    @PutMapping("/{id}") Details update(Principal user, @PathVariable UUID id, @Valid @RequestBody Update request) {
        return service.update(user.getName(), id, request);
    }
    @DeleteMapping("/{id}") ResponseEntity<Void> archive(Principal user, @PathVariable UUID id) {
        service.archive(user.getName(), id);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}/versions") PageResult<Version> history(Principal user, @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.history(user.getName(), id, page, size);
    }
}
