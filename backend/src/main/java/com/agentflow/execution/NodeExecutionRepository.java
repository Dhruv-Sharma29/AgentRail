package com.agentflow.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface NodeExecutionRepository extends JpaRepository<NodeExecution, UUID> {
    List<NodeExecution> findByRunIdOrderByOrdinal(UUID runId);
}
