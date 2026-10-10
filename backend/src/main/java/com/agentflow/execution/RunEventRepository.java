package com.agentflow.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface RunEventRepository extends JpaRepository<RunEvent, UUID> {
    List<RunEvent> findByRunIdOrderBySequence(UUID runId);
    long countByRunId(UUID runId);
}
