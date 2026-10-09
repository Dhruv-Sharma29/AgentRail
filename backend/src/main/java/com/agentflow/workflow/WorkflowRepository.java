package com.agentflow.workflow;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {
    Page<Workflow> findByOwnerIdAndArchivedFalse(UUID ownerId, Pageable pageable);
    Optional<Workflow> findByIdAndOwnerId(UUID id, UUID ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Workflow w where w.id = :id and w.ownerId = :ownerId")
    Optional<Workflow> findForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
