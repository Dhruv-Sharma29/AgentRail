ALTER TABLE node_executions ADD COLUMN ticket_settings TEXT;
ALTER TABLE node_executions DROP CONSTRAINT ck_node_status;
ALTER TABLE node_executions ADD CONSTRAINT ck_node_status CHECK (status IN ('PENDING','RUNNING','WAITING_FOR_APPROVAL','SUCCEEDED','FAILED','CANCELLED'));
ALTER TABLE node_executions ADD CONSTRAINT ck_ticket_settings CHECK (
    (operation = 'CREATE_TICKET' AND ticket_settings IS NOT NULL)
    OR ((operation IS NULL OR operation <> 'CREATE_TICKET') AND ticket_settings IS NULL)
);
CREATE TABLE ticket_executions (
    id UUID PRIMARY KEY REFERENCES node_executions(id),
    run_id UUID NOT NULL REFERENCES workflow_runs(id),
    node_id VARCHAR(64) NOT NULL,
    arguments TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('SENDING','SUCCEEDED','FAILED','UNKNOWN')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    issue_number INTEGER,
    issue_url VARCHAR(300),
    error_code VARCHAR(64),
    CONSTRAINT uq_ticket_run UNIQUE (run_id),
    CONSTRAINT ticket_receipt CHECK ((status = 'SUCCEEDED' AND issue_number > 0 AND issue_url IS NOT NULL)
        OR (status <> 'SUCCEEDED' AND issue_number IS NULL AND issue_url IS NULL))
);
CREATE INDEX idx_ticket_pending_deadline ON ticket_executions(status, deadline);
