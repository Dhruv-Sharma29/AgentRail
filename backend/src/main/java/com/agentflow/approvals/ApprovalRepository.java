package com.agentflow.approvals;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ApprovalRepository extends JpaRepository<ApprovalRequest,UUID> {
    List<ApprovalRequest> findByRunIdOrderByCreatedAt(UUID runId);
    Optional<ApprovalRequest> findByTargetNodeExecutionId(UUID target);
    Optional<ApprovalRequest> findByIdAndOwnerId(UUID id,UUID owner);
    Page<ApprovalRequest> findByOwnerIdAndStatus(UUID owner,ApprovalRequest.Status status,Pageable page);
    @Query("select a.runId from ApprovalRequest a where a.id=:id and a.ownerId=:owner")
    Optional<UUID> ownedRun(@Param("id")UUID id,@Param("owner")UUID owner);
}
