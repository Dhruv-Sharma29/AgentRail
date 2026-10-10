package com.agentflow.execution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface TicketRepository extends JpaRepository<TicketExecution,UUID> {
    Optional<TicketExecution> findByRunId(UUID run);
}
