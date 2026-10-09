package com.agentflow.workflow;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, UUID> {
    Optional<WorkflowVersion> findByWorkflowIdAndNumber(UUID workflowId, int number);
    Page<WorkflowVersion> findByWorkflowId(UUID workflowId, Pageable pageable);
}
