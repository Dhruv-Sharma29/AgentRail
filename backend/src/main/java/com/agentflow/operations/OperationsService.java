package com.agentflow.operations;

import com.agentflow.auth.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
public class OperationsService {
    private final UserRepository users;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public OperationsService(UserRepository users, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.users = users; this.jdbc = jdbc; this.clock = clock;
    }

    public record Snapshot(Instant capturedAt, long totalRuns, long pendingRuns, long runningRuns,
            long waitingRuns, long succeededRuns, long failedRuns, long cancelledRuns,
            long pendingApprovals, long overdueApprovals, long retryingSteps, long unknownTickets, long sendingTickets) {}

    @Transactional(readOnly = true)
    public Snapshot snapshot(String username) {
        var user = users.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!user.getRole().equals("USER")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var now = clock.instant();
        // One SQL statement keeps queue and lifecycle counts on the same database snapshot.
        return jdbc.queryForObject("""
                with owned_runs as (select id, status from workflow_runs where owner_id=:owner),
                approvals as (
                    select count(*) as pending, count(case when a.expires_at<=:now then 1 end) as overdue
                    from approval_requests a join owned_runs r on r.id=a.run_id
                    where a.owner_id=:owner and a.status='PENDING' and r.status='WAITING_FOR_APPROVAL'
                ), tickets as (
                    select count(case when t.status='UNKNOWN' then 1 end) as unknown_count,
                           count(case when t.status='SENDING' then 1 end) as sending
                    from ticket_executions t join owned_runs r on r.id=t.run_id
                ), retries as (
                    select count(*) as scheduled from node_executions n join owned_runs r on r.id=n.run_id
                    where r.status in ('PENDING','RUNNING') and n.status='PENDING' and n.next_attempt_at is not null
                )
                select count(*) as total,
                    count(case when status='PENDING' then 1 end) as pending,
                    count(case when status='RUNNING' then 1 end) as running,
                    count(case when status='WAITING_FOR_APPROVAL' then 1 end) as waiting,
                    count(case when status='SUCCEEDED' then 1 end) as succeeded,
                    count(case when status='FAILED' then 1 end) as failed,
                    count(case when status='CANCELLED' then 1 end) as cancelled,
                    (select pending from approvals) as pending_approvals,
                    (select overdue from approvals) as overdue_approvals,
                    (select scheduled from retries) as retrying_steps,
                    (select unknown_count from tickets) as unknown_tickets,
                    (select sending from tickets) as sending_tickets
                from owned_runs
                """, Map.of("owner", user.getId(), "now", Timestamp.from(now)), (row, index) -> new Snapshot(now,
                row.getLong("total"), row.getLong("pending"), row.getLong("running"), row.getLong("waiting"),
                row.getLong("succeeded"), row.getLong("failed"), row.getLong("cancelled"),
                row.getLong("pending_approvals"), row.getLong("overdue_approvals"), row.getLong("retrying_steps"),
                row.getLong("unknown_tickets"), row.getLong("sending_tickets")));
    }
}
