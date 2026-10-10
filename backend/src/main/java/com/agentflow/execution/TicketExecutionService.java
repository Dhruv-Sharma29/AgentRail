package com.agentflow.execution;

import com.agentflow.auth.UserRepository;
import com.agentflow.tools.*;
import com.agentflow.workflow.WorkflowDefinition.Operation;
import com.fasterxml.jackson.databind.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

/** The run lock serializes one durable dispatch. Network calls belong to TicketCoordinator. */
@Service
public class TicketExecutionService {
    public record Job(UUID id,UUID runId,TicketPolicy.Arguments arguments,Instant deadline) {}
    public record Claim(boolean handled,Job job) {}
    private final RunRepository runs;
    private final NodeExecutionRepository steps;
    private final TicketRepository tickets;
    private final RunEventRepository events;
    private final ApprovalService approvals;
    private final UserRepository users;
    private final ObjectMapper json;
    private final Clock clock;
    private final TicketPolicy policy;
    public TicketExecutionService(RunRepository runs,NodeExecutionRepository steps,TicketRepository tickets,RunEventRepository events,
            ApprovalService approvals,UserRepository users,ObjectMapper json,Clock clock,TicketPolicy policy) {
        this.runs=runs; this.steps=steps; this.tickets=tickets; this.events=events; this.approvals=approvals;
        this.users=users; this.json=json; this.clock=clock; this.policy=policy;
    }
    @Transactional
    public Claim claim(UUID id) {
        var run=runs.lock(id).orElseThrow(); var now=clock.instant();
        var existing=tickets.findByRunId(id);
        if(existing.isPresent()) {
            var intent=existing.get();
            if(intent.status.equals("SENDING") && !now.isBefore(intent.deadline))
                outcome(run,intent,null,"TICKET_OUTCOME_UNKNOWN",now,false);
            return new Claim(true,null);
        }
        if(run.status!=RunStatus.RUNNING) return new Claim(false,null);
        var trace=steps.findByRunIdOrderByOrdinal(id);
        var next=trace.stream().filter(s -> s.status!=RunStatus.SUCCEEDED).findFirst().orElse(null);
        if(next==null || next.operation!=Operation.CREATE_TICKET) return new Claim(false,null);
        try {
            if(!now.isBefore(run.executionDeadline())) throw new ToolRegistry.Denied("RUN_DEADLINE_EXCEEDED");
            if(run.toolCalls>=com.agentflow.agent.ProposalPolicy.MAX_RUN_TOOL_CALLS) throw new ToolRegistry.Denied("TOOL_BUDGET_EXHAUSTED");
            var args=approvals.validateAction(run,next);
            policy.validate(args,users.findById(run.ownerId).orElseThrow().getRole());
            var deadline=now.plusSeconds(20).isBefore(run.executionDeadline()) ? now.plusSeconds(20) : run.executionDeadline();
            var intent=new TicketExecution(next,write(args),now,deadline);
            tickets.saveAndFlush(intent);
            next.input=run.context; next.startedAt=now; next.status=RunStatus.RUNNING; next.attempts++;
            next.proposal=write(Map.of("toolCalls",List.of(Map.of("tool","create_ticket","arguments",args))));
            next.policyStatus="APPROVED"; next.decisionCode="APPROVED"; run.toolCalls++;
            event(run,next.nodeId,"TICKET_DISPATCHED",null,now);
            return new Claim(true,new Job(intent.id,id,args,deadline));
        } catch(ToolRegistry.Denied denied) {
            next.policyStatus="DENIED"; next.decisionCode=denied.getMessage(); next.fail(run.context,denied.getMessage(),now);
            run.finish(RunStatus.FAILED,now,denied.getMessage()); event(run,next.nodeId,"STEP_FAILED",denied.getMessage(),now);
            event(run,null,"RUN_FAILED",denied.getMessage(),now); return new Claim(true,null);
        }
    }
    @Transactional
    public void complete(Job job,GitHubTicketClient.Result result,String failure) {
        var run=runs.lock(job.runId()).orElseThrow(); var intent=tickets.findById(job.id()).orElseThrow();
        if(!intent.runId.equals(job.runId()) || !read(intent.arguments).equals(job.arguments())) throw new IllegalStateException("Ticket intent mismatch");
        if(!Set.of("SENDING","UNKNOWN").contains(intent.status)) return;
        String code=failure;
        if(result!=null && (result.number()<1 || !result.url().equals("https://github.com/"+job.arguments().repository()+"/issues/"+result.number()))) {
            result=null; code="TICKET_OUTCOME_UNKNOWN";
        }
        if(result==null && !Set.of("TICKET_OUTCOME_UNKNOWN","TICKET_AUTHENTICATION","TICKET_REJECTED","TICKET_NOT_DISPATCHED").contains(code==null?"":code))
            code="TICKET_OUTCOME_UNKNOWN";
        if(intent.status.equals("UNKNOWN") && result==null) return;
        outcome(run,intent,result,code,clock.instant(),false);
    }
    @Transactional
    public Job reconciliation(String username,UUID id,int issueNumber) {
        var owner=users.findByUsername(username).orElseThrow(TicketExecutionService::notFound);
        var run=runs.lockOwned(id,owner.getId()).orElseThrow(TicketExecutionService::notFound);
        var intent=tickets.findByRunId(id).orElseThrow(TicketExecutionService::notFound);
        if(!owner.getRole().equals("USER")) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Ticket role denied");
        if(intent.status.equals("SUCCEEDED") && Objects.equals(intent.issueNumber,issueNumber)) return null;
        if(!intent.status.equals("UNKNOWN")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only an unknown ticket outcome can be reconciled");
        var target=steps.findById(intent.id).orElseThrow(); var args=approvals.validateAction(run,target);
        if(!args.equals(read(intent.arguments))) throw new ToolRegistry.Denied("APPROVAL_PAYLOAD_CHANGED");
        policy.validate(args,owner.getRole());
        return new Job(intent.id,id,args,clock.instant().plusSeconds(10));
    }
    @Transactional
    public void reconciled(Job job,GitHubTicketClient.Result result) {
        var run=runs.lock(job.runId()).orElseThrow(); var intent=tickets.findById(job.id()).orElseThrow();
        if(intent.status.equals("SUCCEEDED")) return;
        if(!intent.status.equals("UNKNOWN") || !read(intent.arguments).equals(job.arguments())
                || result.number()<1 || !result.url().equals("https://github.com/"+job.arguments().repository()+"/issues/"+result.number()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Ticket changed during reconciliation");
        var target=steps.findById(intent.id).orElseThrow(); approvals.validateAction(run,target);
        outcome(run,intent,result,null,clock.instant(),true);
    }
    private void outcome(WorkflowRun run,TicketExecution intent,GitHubTicketClient.Result result,String code,Instant now,boolean reconciliation) {
        intent.status=result!=null ? "SUCCEEDED" : code.equals("TICKET_OUTCOME_UNKNOWN") ? "UNKNOWN" : "FAILED";
        intent.errorCode=result!=null ? null : code; intent.finishedAt=now;
        intent.issueNumber=result==null ? null : result.number(); intent.issueUrl=result==null ? null : result.url();
        var step=steps.findById(intent.id).orElseThrow();
        event(run,intent.nodeId,reconciliation ? "TICKET_RECONCILED" : "TICKET_"+intent.status,intent.errorCode,now);
        if(run.status==RunStatus.CANCELLED) { event(run,intent.nodeId,"TICKET_RESULT_AFTER_CANCELLATION",intent.errorCode,now); return; }
        step.finishedAt=now; step.errorCode=intent.errorCode;
        if(result==null) {
            step.status=RunStatus.FAILED; run.finish(RunStatus.FAILED,now,code);
            event(run,intent.nodeId,"STEP_FAILED",code,now); event(run,null,"RUN_FAILED",code,now);
        } else {
            var context=tree(run.context); var output=context.putObject("result");
            output.put("subject",read(intent.arguments).title()); output.put("externalTicketCreated",true);
            output.put("issueNumber",result.number()); output.put("issueUrl",result.url());
            output.put("repository",read(intent.arguments).repository());
            step.status=RunStatus.SUCCEEDED; step.output=write(context); run.context=step.output;
            run.finish(RunStatus.SUCCEEDED,now,null);
            event(run,intent.nodeId,"STEP_SUCCEEDED",null,now); event(run,null,"RUN_SUCCEEDED",null,now);
        }
    }
    private void event(WorkflowRun run,String node,String type,String code,Instant now) {
        var event=new RunEvent(run.id,Math.toIntExact(events.countByRunId(run.id)),node,type,now);
        event.toolName="create_ticket"; event.decisionCode=code; events.save(event);
    }
    private TicketPolicy.Arguments read(String value) {
        try { return json.readerFor(TicketPolicy.Arguments.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readValue(value); }
        catch(Exception e) { throw new ToolRegistry.Denied("APPROVAL_PAYLOAD_CHANGED"); }
    }
    private com.fasterxml.jackson.databind.node.ObjectNode tree(String value) {
        try { return (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(value); }
        catch(Exception e) { throw new ToolRegistry.Denied("APPROVAL_PAYLOAD_CHANGED"); }
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException("Invalid ticket state"); } }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Ticket not found"); }
}
