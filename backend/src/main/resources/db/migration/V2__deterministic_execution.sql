CREATE TABLE workflow_runs (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL REFERENCES workflows(id),
    version_id UUID NOT NULL REFERENCES workflow_versions(id),
    version_number INTEGER NOT NULL CHECK (version_number >= 1),
    owner_id UUID NOT NULL REFERENCES users(id),
    idempotency_key VARCHAR(100) NOT NULL,
    input TEXT NOT NULL,
    context TEXT NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    error_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    revision BIGINT NOT NULL DEFAULT 0,
    UNIQUE (owner_id, workflow_id, idempotency_key)
);
CREATE INDEX ix_runs_workflow_created ON workflow_runs(workflow_id, created_at);
CREATE INDEX ix_runs_status_created ON workflow_runs(status, created_at);

CREATE TABLE node_executions (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES workflow_runs(id),
    node_id VARCHAR(64) NOT NULL,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    operation VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING','SUCCEEDED','FAILED','CANCELLED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    input TEXT,
    output TEXT,
    error_code VARCHAR(64),
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (run_id, node_id),
    UNIQUE (run_id, ordinal)
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES workflow_runs(id),
    sequence INTEGER NOT NULL CHECK (sequence >= 0),
    node_id VARCHAR(64),
    event_type VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (run_id, sequence)
);
