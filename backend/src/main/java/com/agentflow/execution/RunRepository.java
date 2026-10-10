package com.agentflow.execution;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

interface RunRepository extends JpaRepository<WorkflowRun, UUID> {
    Optional<WorkflowRun> findByIdAndOwnerId(UUID id, UUID ownerId);
    Optional<WorkflowRun> findByOwnerIdAndWorkflowIdAndIdempotencyKey(UUID ownerId, UUID workflowId, String key);
    Page<WorkflowRun> findByWorkflowIdAndOwnerId(UUID workflowId, UUID ownerId, Pageable page);
    @Query("select r.id from WorkflowRun r where r.status in :statuses order by r.createdAt, r.id")
    List<UUID> findRunnable(@Param("statuses") Collection<RunStatus> statuses, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from WorkflowRun r where r.id = :id")
    Optional<WorkflowRun> lock(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from WorkflowRun r where r.id = :id and r.ownerId = :owner")
    Optional<WorkflowRun> lockOwned(@Param("id") UUID id, @Param("owner") UUID owner);
}
