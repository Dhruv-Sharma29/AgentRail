package com.agentflow.approvals;

import com.agentflow.execution.ApprovalService;
import com.agentflow.workflow.WorkflowDtos.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;

@RestController @RequestMapping("/api/approvals")
public class ApprovalController {
    private final ApprovalService approvals;
    public ApprovalController(ApprovalService approvals){this.approvals=approvals;}
    @GetMapping PageResult<ApprovalDtos.View> inbox(Principal user,@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return approvals.inbox(user.getName(),page,size); }
    @GetMapping("/{id}") ApprovalDtos.View get(Principal user,@PathVariable UUID id) { return approvals.get(user.getName(),id); }
    @PostMapping("/{id}/decision") ResponseEntity<ApprovalDtos.View> decide(Principal user,@PathVariable UUID id,@Valid @RequestBody ApprovalDtos.Decide request) {
        var view=approvals.decide(user.getName(),id,request);
        return ResponseEntity.status(view.status()==ApprovalRequest.Status.EXPIRED ? 409 : 200).body(view);
    }
}
