package com.agentflow.operations;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;

@RestController
public class OperationsController {
    private final OperationsService service;
    public OperationsController(OperationsService service) { this.service = service; }

    @GetMapping("/api/operations")
    ResponseEntity<OperationsService.Snapshot> snapshot(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.snapshot(principal.getName()));
    }
}
