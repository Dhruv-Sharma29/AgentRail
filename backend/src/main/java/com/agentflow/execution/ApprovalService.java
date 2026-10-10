package com.agentflow.execution;

import com.agentflow.approvals.*;
import com.agentflow.auth.UserRepository;
import com.agentflow.tools.ToolRegistry;
import com.agentflow.workflow.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static com.agentflow.approvals.ApprovalRequest.Status.*;

/** Execution owns the run lock; approval persistence lives in the approvals module. */
@Service
public class ApprovalService {
    private final ApprovalRepository approvals;
    private final RunRepository runs;
    private final NodeExecutionRepository steps;
    private final RunEventRepository events;
    private final UserRepository users;
    private final ObjectMapper json;
    private final Clock clock;
    private final com.agentflow.tools.TicketPolicy ticketPolicy;
    public ApprovalService(ApprovalRepository approvals,RunRepository runs,NodeExecutionRepository steps,RunEventRepository events,
            UserRepository users,ObjectMapper json,Clock clock,com.agentflow.tools.TicketPolicy ticketPolicy) {
        this.approvals=approvals; this.runs=runs; this.steps=steps; this.events=events; this.users=users; this.json=json; this.clock=clock;
        this.ticketPolicy=ticketPolicy;
    }
    record ActionPayload(String targetNodeId,WorkflowDefinition.Operation operation,ExecutionDtos.IssueInput input,JsonNode context,
            com.agentflow.tools.TicketPolicy.Arguments ticket) {}

    void pause(WorkflowRun run,NodeExecution step,List<NodeExecution> trace,Instant now) {
        var target=trace.stream().filter(s -> s.ordinal==step.ordinal+1).findFirst().orElseThrow();
        if(!Set.of(WorkflowDefinition.Operation.RECORD_RESULT,WorkflowDefinition.Operation.CREATE_TICKET).contains(target.operation)
                || target.agentSettings!=null || target.approvalSettings!=null)
            throw new ToolRegistry.Denied("APPROVAL_INVALID_TARGET");
        var settings=read(step.approvalSettings,WorkflowDefinition.ApprovalSettings.class);
        var input=read(run.input,ExecutionDtos.IssueInput.class); var context=read(run.context,JsonNode.class);
        var ticket=target.operation==WorkflowDefinition.Operation.CREATE_TICKET
                ? ticketPolicy.propose(target.id,read(target.ticketSettings,WorkflowDefinition.TicketSettings.class),input,context) : null;
        var payload=new ActionPayload(target.nodeId,target.operation,input,context,ticket);
        approvals.save(new ApprovalRequest(run.id,run.ownerId,step.id,target.id,step.nodeId,target.nodeId,write(payload),now,now.plusSeconds(settings.timeoutSeconds())));
        step.pause(run.context,now); run.status=RunStatus.WAITING_FOR_APPROVAL;
        event(run,step.nodeId,"APPROVAL_REQUESTED",now);
    }

    void checkExpiry(WorkflowRun run,List<NodeExecution> trace,Instant now) {
        for(var approval:approvals.findByRunIdOrderByCreatedAt(run.id)) if(approval.status()==PENDING && !now.isBefore(approval.expiresAt())) {
            terminate(run,trace,approval,EXPIRED,"APPROVAL_EXPIRED",null,now); return;
        }
    }
    void cancelPending(WorkflowRun run,Instant now) {
        for(var approval:approvals.findByRunIdOrderByCreatedAt(run.id)) if(approval.status()==PENDING) {
            approval.decide(CANCELLED,null,now); event(run,approval.nodeId(),"APPROVAL_CANCELLED",now);
        }
    }
    com.agentflow.tools.TicketPolicy.Arguments validateAction(WorkflowRun run,NodeExecution target) {
        var approval=approvals.findByTargetNodeExecutionId(target.id).orElseThrow(() -> new ToolRegistry.Denied("APPROVAL_REQUIRED"));
        if(approval.status()!=APPROVED) throw new ToolRegistry.Denied("APPROVAL_REQUIRED");
        requireRole(run.ownerId);
        var payload=read(approval.payload(),ActionPayload.class);
        if(!approval.runId().equals(run.id) || !approval.ownerId().equals(run.ownerId) || !approval.requiredRole().equals("USER")
                || !approval.payloadDigest().equals(ApprovalRequest.digest(approval.payload()))
                || !payload.targetNodeId().equals(target.nodeId) || !approval.targetNodeId().equals(target.nodeId)
                || payload.operation()!=target.operation || !payload.input().equals(read(run.input,ExecutionDtos.IssueInput.class))
                || !payload.context().equals(read(run.context,JsonNode.class))) throw new ToolRegistry.Denied("APPROVAL_PAYLOAD_CHANGED");
        if(target.operation==WorkflowDefinition.Operation.CREATE_TICKET) {
            var expected=ticketPolicy.propose(target.id,read(target.ticketSettings,WorkflowDefinition.TicketSettings.class),payload.input(),payload.context());
            if(!expected.equals(payload.ticket())) throw new ToolRegistry.Denied("APPROVAL_PAYLOAD_CHANGED");
        } else if(payload.ticket()!=null) throw new ToolRegistry.Denied("APPROVAL_PAYLOAD_CHANGED");
        return payload.ticket();
    }
    @Transactional(readOnly=true)
    public List<ApprovalDtos.View> trace(UUID run) { return approvals.findByRunIdOrderByCreatedAt(run).stream().map(this::view).toList(); }
    @Transactional(readOnly=true)
    public ApprovalDtos.View get(String username,UUID id) {
        return view(approvals.findByIdAndOwnerId(id,owner(username)).orElseThrow(ApprovalService::notFound));
    }
    @Transactional(readOnly=true)
    public WorkflowDtos.PageResult<ApprovalDtos.View> inbox(String username,int page,int size) {
        var found=approvals.findByOwnerIdAndStatus(owner(username),PENDING,PageRequest.of(page,size,Sort.by("createdAt","id")));
        return new WorkflowDtos.PageResult<>(found.stream().map(this::view).toList(),found.getTotalElements(),page,size);
    }
    @Transactional
    public ApprovalDtos.View decide(String username,UUID id,ApprovalDtos.Decide request) {
        var owner=owner(username);
        // Read only the run ID before locking: do not cache an approval entity before a competing decision commits.
        var runId=approvals.ownedRun(id,owner).orElseThrow(ApprovalService::notFound);
        var run=runs.lockOwned(runId,owner).orElseThrow(ApprovalService::notFound);
        var approval=approvals.findByIdAndOwnerId(id,owner).orElseThrow(ApprovalService::notFound);
        if(!"USER".equals(users.findById(owner).orElseThrow().getRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Approval role denied");
        if(!approval.payloadDigest().equals(request.payloadDigest()) || !approval.payloadDigest().equals(ApprovalRequest.digest(approval.payload())))
            throw conflict("Approval payload changed; reload");
        var next=request.decision()==ApprovalDtos.Decision.APPROVE ? APPROVED : REJECTED;
        if(approval.status()==next) return view(approval);
        if(approval.status()!=PENDING || run.status!=RunStatus.WAITING_FOR_APPROVAL) throw conflict("Approval is no longer pending");
        var trace=steps.findByRunIdOrderByOrdinal(run.id);
        var now=clock.instant();
        if(!now.isBefore(approval.expiresAt())) {
            terminate(run,trace,approval,EXPIRED,"APPROVAL_EXPIRED",null,now); return view(approval);
        }
        if(next==REJECTED) terminate(run,trace,approval,REJECTED,"APPROVAL_REJECTED",owner,now);
        else {
            var step=trace.stream().filter(s -> s.id.equals(approval.nodeExecutionId())).findFirst().orElseThrow();
            if(step.status!=RunStatus.WAITING_FOR_APPROVAL) throw conflict("Approval checkpoint is no longer waiting");
            approval.decide(APPROVED,owner,now);
            run.pausedMillis+=Math.max(0,Duration.between(approval.createdAt(),now).toMillis());
            step.approved(now); run.status=RunStatus.RUNNING;
            event(run,step.nodeId,"APPROVAL_APPROVED",now); event(run,step.nodeId,"STEP_SUCCEEDED",now);
        }
        return view(approval);
    }
    private void terminate(WorkflowRun run,List<NodeExecution> trace,ApprovalRequest approval,ApprovalRequest.Status status,String code,UUID actor,Instant now) {
        approval.decide(status,actor,now);
        var step=trace.stream().filter(s -> s.id.equals(approval.nodeExecutionId())).findFirst().orElseThrow();
        step.fail(run.context,code,now);
        trace.stream().filter(s -> s.status==RunStatus.PENDING).forEach(s -> s.cancel(now));
        run.finish(RunStatus.FAILED,now,code);
        event(run,step.nodeId,status==EXPIRED ? "APPROVAL_EXPIRED" : "APPROVAL_REJECTED",now);
        event(run,step.nodeId,"STEP_FAILED",now); event(run,null,"RUN_FAILED",now);
    }
    private ApprovalDtos.View view(ApprovalRequest a) {
        return new ApprovalDtos.View(a.id(),a.runId(),a.nodeId(),a.targetNodeId(),a.requiredRole(),a.status(),read(a.payload(),JsonNode.class),
                a.payloadDigest(),a.createdAt(),a.expiresAt(),a.decidedAt(),a.decidedBy());
    }
    private void requireRole(UUID owner) { if(!"USER".equals(users.findById(owner).orElseThrow().getRole())) throw new ToolRegistry.Denied("TOOL_ROLE_DENIED"); }
    private UUID owner(String username) { return users.findByUsername(username).orElseThrow(ApprovalService::notFound).getId(); }
    private static ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Approval not found");}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private void event(WorkflowRun run,String node,String type,Instant now){events.save(new RunEvent(run.id,Math.toIntExact(events.countByRunId(run.id)),node,type,now));}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Invalid approval payload");}}
    private <T> T read(String value,Class<T> type){
        try{return json.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readValue(value);}
        catch(Exception e){throw new ToolRegistry.Denied("APPROVAL_PAYLOAD_CHANGED");}
    }
}
