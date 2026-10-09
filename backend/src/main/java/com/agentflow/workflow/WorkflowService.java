package com.agentflow.workflow;

import com.agentflow.auth.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static com.agentflow.workflow.WorkflowDtos.*;

@Service
@Transactional(readOnly = true)
public class WorkflowService {
    private final WorkflowRepository workflows;
    private final WorkflowVersionRepository versions;
    private final UserRepository users;
    private final GraphValidator validator;
    private final ObjectMapper json;

    public WorkflowService(WorkflowRepository workflows, WorkflowVersionRepository versions,
            UserRepository users, GraphValidator validator, ObjectMapper json) {
        this.workflows = workflows; this.versions = versions; this.users = users;
        this.validator = validator; this.json = json;
    }

    @Transactional
    public Details create(String username, Create request) {
        validator.validate(request.definition());
        var workflow = workflows.saveAndFlush(new Workflow(owner(username), request.name(), request.description()));
        versions.save(new WorkflowVersion(workflow, encode(request.definition())));
        return details(workflow, request.definition());
    }

    public PageResult<Summary> list(String username, int page, int size) {
        var result = workflows.findByOwnerIdAndArchivedFalse(owner(username),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new PageResult<>(result.stream().map(w -> new Summary(w.getId(), w.getName(), w.getDescription(),
                w.getCurrentVersion(), w.getUpdatedAt())).toList(), result.getTotalElements(), page, size);
    }

    public Details get(String username, UUID id) {
        var workflow = owned(username, id);
        var version = versions.findByWorkflowIdAndNumber(id, workflow.getCurrentVersion()).orElseThrow();
        return details(workflow, decode(version.getDefinition()));
    }

    @Transactional
    public Details update(String username, UUID id, Update request) {
        var workflow = workflows.findForUpdate(id, owner(username)).orElseThrow(WorkflowService::notFound);
        if (workflow.isArchived()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived workflows cannot be edited");
        if (workflow.getCurrentVersion() != request.expectedVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The workflow changed; reload before editing");
        }
        validator.validate(request.definition());
        workflow.update(request.name(), request.description());
        versions.save(new WorkflowVersion(workflow, encode(request.definition())));
        return details(workflow, request.definition());
    }

    @Transactional
    public void archive(String username, UUID id) {
        var workflow = workflows.findForUpdate(id, owner(username)).orElseThrow(WorkflowService::notFound);
        if (!workflow.isArchived()) workflow.archive();
    }

    public PageResult<Version> history(String username, UUID id, int page, int size) {
        owned(username, id);
        var result = versions.findByWorkflowId(id, PageRequest.of(page, size, Sort.by("number")));
        return new PageResult<>(result.stream().map(v -> new Version(v.getId(), v.getNumber(), v.getName(),
                v.getDescription(), decode(v.getDefinition()), v.getCreatedAt())).toList(), result.getTotalElements(), page, size);
    }

    private Workflow owned(String username, UUID id) {
        return workflows.findByIdAndOwnerId(id, owner(username)).orElseThrow(WorkflowService::notFound);
    }
    private UUID owner(String username) { return users.findByUsername(username).orElseThrow(WorkflowService::notFound).getId(); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"); }
    private Details details(Workflow w, WorkflowDefinition definition) {
        return new Details(w.getId(), w.getName(), w.getDescription(), w.getCurrentVersion(), w.isArchived(),
                w.getCreatedAt(), w.getUpdatedAt(), definition);
    }
    private String encode(WorkflowDefinition definition) {
        try { return json.writeValueAsString(definition); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot persist workflow definition", exception); }
    }
    private WorkflowDefinition decode(String definition) {
        try { return json.readValue(definition, WorkflowDefinition.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid persisted workflow definition", exception); }
    }
}
