ALTER TABLE workflow_runs ADD COLUMN mode VARCHAR(32) NOT NULL DEFAULT 'DETERMINISTIC';
ALTER TABLE workflow_runs ADD COLUMN planner_calls INTEGER NOT NULL DEFAULT 0 CHECK (planner_calls BETWEEN 0 AND 5);
ALTER TABLE workflow_runs ADD COLUMN tool_calls INTEGER NOT NULL DEFAULT 0 CHECK (tool_calls BETWEEN 0 AND 10);

ALTER TABLE node_executions ALTER COLUMN operation DROP NOT NULL;
ALTER TABLE node_executions ADD COLUMN agent_settings TEXT;
ALTER TABLE node_executions ADD COLUMN planner_id VARCHAR(64);
ALTER TABLE node_executions ADD COLUMN proposal TEXT;
ALTER TABLE node_executions ADD COLUMN policy_status VARCHAR(20) CHECK (policy_status IN ('APPROVED', 'DENIED'));
ALTER TABLE node_executions ADD COLUMN decision_code VARCHAR(64);
ALTER TABLE node_executions ADD CONSTRAINT ck_node_execution_kind CHECK (
    (operation IS NOT NULL AND agent_settings IS NULL) OR (operation IS NULL AND agent_settings IS NOT NULL)
);

ALTER TABLE audit_events ADD COLUMN tool_name VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN decision_code VARCHAR(64);
