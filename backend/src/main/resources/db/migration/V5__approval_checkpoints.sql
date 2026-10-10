-- Copy status values before replacing the old, unnamed CHECK constraints.
-- Drop the index explicitly: H2 refuses to drop an indexed column.
ALTER TABLE workflow_runs ADD COLUMN approval_status_copy VARCHAR(30);
UPDATE workflow_runs SET approval_status_copy = status;
DROP INDEX ix_runs_status_created;
ALTER TABLE workflow_runs DROP COLUMN status;
ALTER TABLE workflow_runs RENAME COLUMN approval_status_copy TO status;
ALTER TABLE workflow_runs ALTER COLUMN status SET NOT NULL;
ALTER TABLE workflow_runs ADD CONSTRAINT ck_run_status CHECK (status IN ('PENDING','RUNNING','WAITING_FOR_APPROVAL','SUCCEEDED','FAILED','CANCELLED'));
CREATE INDEX ix_runs_status_created ON workflow_runs(status, created_at);
ALTER TABLE workflow_runs ADD COLUMN paused_millis BIGINT NOT NULL DEFAULT 0 CHECK (paused_millis >= 0);

ALTER TABLE node_executions ADD COLUMN approval_status_copy VARCHAR(30);
UPDATE node_executions SET approval_status_copy = status;
ALTER TABLE node_executions DROP COLUMN status;
ALTER TABLE node_executions RENAME COLUMN approval_status_copy TO status;
ALTER TABLE node_executions ALTER COLUMN status SET NOT NULL;
ALTER TABLE node_executions ADD CONSTRAINT ck_node_status CHECK (status IN ('PENDING','WAITING_FOR_APPROVAL','SUCCEEDED','FAILED','CANCELLED'));
ALTER TABLE node_executions ADD COLUMN approval_settings TEXT;
ALTER TABLE node_executions DROP CONSTRAINT ck_node_execution_kind;
ALTER TABLE node_executions ADD CONSTRAINT ck_node_execution_kind CHECK (
    (operation IS NOT NULL AND agent_settings IS NULL AND approval_settings IS NULL)
    OR (operation IS NULL AND agent_settings IS NOT NULL AND approval_settings IS NULL)
    OR (operation IS NULL AND agent_settings IS NULL AND approval_settings IS NOT NULL)
);

CREATE TABLE approval_requests (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES workflow_runs(id),
    owner_id UUID NOT NULL REFERENCES users(id),
    node_execution_id UUID NOT NULL UNIQUE REFERENCES node_executions(id),
    target_node_execution_id UUID NOT NULL UNIQUE REFERENCES node_executions(id),
    node_id VARCHAR(64) NOT NULL,
    target_node_id VARCHAR(64) NOT NULL,
    required_role VARCHAR(30) NOT NULL,
    payload TEXT NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED','EXPIRED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    decided_by UUID REFERENCES users(id),
    UNIQUE(run_id, node_id),
    CHECK (expires_at > created_at),
    CHECK ((status = 'PENDING' AND decided_at IS NULL AND decided_by IS NULL)
        OR (status IN ('APPROVED','REJECTED') AND decided_at IS NOT NULL AND decided_by IS NOT NULL)
        OR (status IN ('CANCELLED','EXPIRED') AND decided_at IS NOT NULL AND decided_by IS NULL))
);
CREATE INDEX ix_approvals_owner_status_created ON approval_requests(owner_id, status, created_at);
CREATE INDEX ix_approvals_status_expires ON approval_requests(status, expires_at);
