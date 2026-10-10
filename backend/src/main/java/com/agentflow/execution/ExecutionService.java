package com.agentflow.execution;

import com.agentflow.auth.UserRepository;
import com.agentflow.agent.*;
import com.agentflow.tools.ToolRegistry;
import com.agentflow.workflow.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.Clock;
import java.util.*;
import static com.agentflow.execution.ExecutionDtos.*;

@Service
@Transactional(readOnly = true)
public class ExecutionService {
    private final RunRepository runs;
    private final NodeExecutionRepository steps;
    private final RunEventRepository events;
    private final WorkflowRepository workflows;
    private final WorkflowVersionRepository versions;
    private final UserRepository users;
    private final ExecutionPlan plan;
    private final ObjectMapper json;
    private final LocalActions actions;
    private final Clock clock;
    private final Planner planner;
    private final ProposalPolicy proposalPolicy;
    private final ToolRegistry tools;

    public ExecutionService(RunRepository runs, NodeExecutionRepository steps, RunEventRepository events,
            WorkflowRepository workflows, WorkflowVersionRepository versions, UserRepository users, ExecutionPlan plan, ObjectMapper json,
            LocalActions actions, Clock clock, Planner planner, ProposalPolicy proposalPolicy, ToolRegistry tools) {
        this.runs = runs; this.steps = steps; this.events = events; this.workflows = workflows;
        this.versions = versions; this.users = users; this.plan = plan; this.json = json;
        this.actions = actions; this.clock = clock;
        this.planner = planner; this.proposalPolicy = proposalPolicy; this.tools = tools;
    }

    @Transactional
    public Details start(String username, UUID workflowId, Start request) {
        var owner = owner(username);
        var workflow = workflows.findForUpdate(workflowId, owner).orElseThrow(ExecutionService::notFound);
        var input = encode(request.input());
        var previous = runs.findByOwnerIdAndWorkflowIdAndIdempotencyKey(owner, workflowId, request.idempotencyKey());
        if (previous.isPresent()) {
            var run = runs.lock(previous.get().id).orElseThrow();
            if (run.versionNumber != request.versionNumber() || !run.input.equals(input))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key is bound to a different request");
            return details(run);
        }
        if (workflow.isArchived()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived workflows cannot start new runs");
        var version = versions.findByWorkflowIdAndNumber(workflowId, request.versionNumber()).orElseThrow(ExecutionService::notFound);
        var ordered = plan.validate(decode(version.getDefinition(), WorkflowDefinition.class));
        var now = clock.instant();
        var run = new WorkflowRun(workflowId, version.getId(), version.getNumber(), owner, request.idempotencyKey(), input, now);
        if (ordered.stream().anyMatch(n -> n.agent() != null)) run.mode = "DETERMINISTIC_PLANNER";
        runs.saveAndFlush(run);
        for (int i = 0; i < ordered.size(); i++) {
            var node = ordered.get(i);
            var step = new NodeExecution(run.id, node.id(), i,
                    node.type() == WorkflowDefinition.NodeType.TRIGGER ? WorkflowDefinition.Operation.RECEIVE_INPUT : node.operation());
            if (node.agent() != null) step.agentSettings = encode(node.agent());
            steps.save(step);
        }
        event(run, null, "RUN_CREATED", now);
        return details(run);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Details get(String username, UUID id) {
        return details(runs.findByIdAndOwnerId(id, owner(username)).orElseThrow(ExecutionService::notFound));
    }
    public WorkflowDtos.PageResult<Summary> history(String username, UUID workflowId, int page, int size) {
        var owner = owner(username);
        workflows.findByIdAndOwnerId(workflowId, owner).orElseThrow(ExecutionService::notFound);
        var found = runs.findByWorkflowIdAndOwnerId(workflowId, owner, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new WorkflowDtos.PageResult<>(found.stream().map(r -> new Summary(r.id, r.workflowId, r.versionId,
                r.versionNumber, r.status, r.createdAt, r.startedAt, r.finishedAt, r.errorCode)).toList(), found.getTotalElements(), page, size);
    }

    @Transactional
    public Details cancel(String username, UUID id) {
        var run = runs.lockOwned(id, owner(username)).orElseThrow(ExecutionService::notFound);
        if (run.status == RunStatus.CANCELLED) return details(run);
        if (run.status.terminal()) throw new ResponseStatusException(HttpStatus.CONFLICT, "A finished run cannot be cancelled");
        var now = clock.instant();
        steps.findByRunIdOrderByOrdinal(id).stream().filter(s -> s.status == RunStatus.PENDING).forEach(s -> s.cancel(now));
        run.finish(RunStatus.CANCELLED, now, null);
        event(run, null, "RUN_CANCELLED", now);
        return details(run);
    }

    public List<UUID> runnable() {
        return runs.findRunnable(List.of(RunStatus.PENDING, RunStatus.RUNNING), PageRequest.of(0, 20));
    }

    /** One short transaction owns the run, its next local step, checkpoint and events. */
    @Transactional
    public void advance(UUID id) {
        var run = runs.lock(id).orElseThrow();
        if (run.status.terminal()) return;
        var now = clock.instant();
        if (run.status == RunStatus.PENDING) {
            run.start(now);
            event(run, null, "RUN_STARTED", now);
        }
        var trace = steps.findByRunIdOrderByOrdinal(id);
        var step = trace.stream().filter(s -> s.status == RunStatus.PENDING).findFirst().orElseThrow();
        var input = step.operation == WorkflowDefinition.Operation.RECEIVE_INPUT ? run.input : run.context;
        String output;
        try {
            if (run.mode.equals("DETERMINISTIC_PLANNER") && !now.isBefore(run.startedAt.plusSeconds(ProposalPolicy.MAX_ELAPSED_SECONDS)))
                throw new ToolRegistry.Denied("RUN_DEADLINE_EXCEEDED");
            var issue = decode(run.input, IssueInput.class);
            var state = decode(run.context, com.fasterxml.jackson.databind.node.ObjectNode.class);
            output = encode(step.agentSettings == null ? actions.execute(step.operation, issue, state) : executeAgent(run, step, issue, state, now));
        } catch (org.springframework.dao.DataAccessException | jakarta.persistence.PersistenceException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            String code = failure instanceof LocalActions.StepFailure || failure instanceof ToolRegistry.Denied ? failure.getMessage() : "INTERNAL_STEP_ERROR";
            if (step.agentSettings != null && !"APPROVED".equals(step.policyStatus)) {
                step.policyStatus = "DENIED"; step.decisionCode = code;
                toolEvent(run, step.nodeId, "PROPOSAL_DENIED", null, code, now);
            }
            step.fail(input, code, now);
            trace.stream().filter(s -> s.status == RunStatus.PENDING).forEach(s -> s.cancel(now));
            run.finish(RunStatus.FAILED, now, code);
            event(run, step.nodeId, "STEP_FAILED", now);
            event(run, null, "RUN_FAILED", now);
            return;
        }
        step.complete(input, output, now);
        run.context = output;
        event(run, step.nodeId, "STEP_SUCCEEDED", now);
        if (trace.stream().noneMatch(s -> s.status == RunStatus.PENDING)) {
            run.finish(RunStatus.SUCCEEDED, clock.instant(), null);
            event(run, null, "RUN_SUCCEEDED", clock.instant());
        }
    }

    private com.fasterxml.jackson.databind.node.ObjectNode executeAgent(WorkflowRun run, NodeExecution step, IssueInput issue,
            com.fasterxml.jackson.databind.node.ObjectNode state, Instant now) {
        if (run.plannerCalls >= ProposalPolicy.MAX_PLANNER_CALLS) throw new ToolRegistry.Denied("PLANNER_BUDGET_EXHAUSTED");
        var settings = decode(step.agentSettings, WorkflowDefinition.AgentSettings.class);
        var allowed = Set.copyOf(settings.allowedTools());
        var user = users.findById(run.ownerId).orElseThrow();
        var context = new ToolRegistry.Context(user.getRole(), issue.subject(), issue.body(), state.path("classification").path("category").asText(null));
        run.plannerCalls++;
        String raw;
        try {
            step.plannerId = planner.id();
            if (step.plannerId == null || !step.plannerId.matches("[a-zA-Z0-9_.-]{1,64}")) throw new IllegalStateException("Invalid planner identity");
            raw = planner.propose(new Planner.Request(settings.goal(), issue.subject(), issue.body(), context.category(), allowed));
        } catch (RuntimeException failure) {
            if (step.plannerId != null && !step.plannerId.matches("[a-zA-Z0-9_.-]{1,64}")) step.plannerId = null;
            throw new ToolRegistry.Denied("PLANNER_FAILURE");
        }
        event(run, step.nodeId, "PLANNER_PROPOSED", now);
        var decision = proposalPolicy.validate(raw, allowed, Math.min(settings.maxToolCalls(), ProposalPolicy.MAX_RUN_TOOL_CALLS - run.toolCalls), context);
        step.proposal = decision.canonicalProposal(); step.policyStatus = "APPROVED"; step.decisionCode = "APPROVED";
        event(run, step.nodeId, "PROPOSAL_APPROVED", now);
        var next = state;
        for (var call : decision.calls()) {
            var validated = tools.validate(call.tool(), call.arguments(), context);
            run.toolCalls++;
            try { next = actions.execute(validated.operation(), issue, next); }
            catch (RuntimeException failure) {
                toolEvent(run, step.nodeId, "TOOL_FAILED", call.tool(), "LOCAL_TOOL_FAILURE", now);
                throw failure;
            }
            toolEvent(run, step.nodeId, "TOOL_SUCCEEDED", call.tool(), null, now);
        }
        return next;
    }

    private UUID owner(String username) { return users.findByUsername(username).orElseThrow(ExecutionService::notFound).getId(); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow, version or run not found"); }
    private void event(WorkflowRun run, String node, String type, Instant now) {
        events.save(new RunEvent(run.id, Math.toIntExact(events.countByRunId(run.id)), node, type, now));
    }
    private void toolEvent(WorkflowRun run, String node, String type, String tool, String code, Instant now) {
        var event = new RunEvent(run.id, Math.toIntExact(events.countByRunId(run.id)), node, type, now);
        event.toolName = tool; event.decisionCode = code; events.save(event);
    }
    private Details details(WorkflowRun r) {
        var trace = steps.findByRunIdOrderByOrdinal(r.id).stream().map(s -> new Step(s.nodeId, s.ordinal, s.operation,
                s.status, s.attempts, tree(s.input), tree(s.output), s.errorCode, s.startedAt, s.finishedAt,
                s.plannerId, tree(s.proposal), s.policyStatus, s.decisionCode)).toList();
        var audit = events.findByRunIdOrderBySequence(r.id).stream().map(e -> new Event(e.sequence, e.nodeId, e.eventType, e.occurredAt, e.toolName, e.decisionCode)).toList();
        return new Details(r.id, r.workflowId, r.versionId, r.versionNumber, r.mode, r.status,
                r.createdAt, r.startedAt, r.finishedAt, r.errorCode, tree(r.input), tree(r.context), trace, audit, r.plannerCalls, r.toolCalls);
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize execution state", exception); }
    }
    private <T> T decode(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid persisted execution state", exception); }
    }
    private JsonNode tree(String value) { return value == null ? null : decode(value, JsonNode.class); }
}
